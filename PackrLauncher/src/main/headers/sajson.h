/*
 * Copyright (c) 2012, 2013, 2014 Chad Austin
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#pragma once

#include <assert.h>
#include <stdarg.h>
#include <stddef.h>
#include <string.h>
#include <math.h>
#include <limits.h>
#include <ostream>
#include <algorithm>
#include <cstdio>
#include <limits>

#include <string> // for error messages.  kill someday?

#if defined(__GNUC__) || defined(__clang__)
#define SAJSON_LIKELY(x) __builtin_expect(!!(x), 1)
#define SAJSON_UNLIKELY(x) __builtin_expect(!!(x), 0)
#else
#define SAJSON_LIKELY(x) x
#define SAJSON_UNLIKELY(x) x
#endif

namespace sajson {
    enum type {
        TYPE_INTEGER = 0,
        TYPE_DOUBLE = 1,
        TYPE_NULL = 2,
        TYPE_FALSE = 3,
        TYPE_TRUE = 4,
        TYPE_STRING = 5,
        TYPE_ARRAY = 6,
        TYPE_OBJECT = 7,
    };

    inline std::ostream& operator<<(std::ostream& os, type t) {
        switch (t) {
            case TYPE_INTEGER: return os << "<integer>";
            case TYPE_DOUBLE:  return os << "<double>";
            case TYPE_NULL:    return os << "<null>";
            case TYPE_FALSE:   return os << "<false>";
            case TYPE_TRUE:    return os << "<true>";
            case TYPE_STRING:  return os << "<string>";
            case TYPE_ARRAY:   return os << "<array>";
            case TYPE_OBJECT:  return os << "<object>";
            default:           return os << "<unknown type";
        }
    }

    static const size_t TYPE_BITS = 3;
    static const size_t TYPE_SHIFT = sizeof(size_t) * 8 - TYPE_BITS;
    static const size_t TYPE_MASK = (1 << TYPE_BITS) - 1;
    static const size_t VALUE_MASK = size_t(-1) >> TYPE_BITS;

    static const size_t ROOT_MARKER = size_t(-1) & VALUE_MASK;

    inline type get_element_type(size_t s) {
        return static_cast<type>((s >> TYPE_SHIFT) & TYPE_MASK);
    }

    inline size_t get_element_value(size_t s) {
        return s & VALUE_MASK;
    }

    inline size_t make_element(type t, size_t value) {
        //assert(value & VALUE_MASK == 0);
        //value &= VALUE_MASK;
        return value | (static_cast<size_t>(t) << TYPE_SHIFT);
    }

    class string {
    public:
        string(const char* text, size_t length)
            : text(text)
            , _length(length)
        {}

        const char* data() const {
            return text;
        }

        const size_t length() const {
            return _length;
        }

        std::string as_string() const {
            return std::string(text, text + _length);
        }

    private:
        const char* const text;
        const size_t _length;

        string(); /*=delete*/
    };

    class literal : public string {
    public:
        explicit literal(const char* text)
            : string(text, strlen(text))
        {}
    };

    struct object_key_record
    {
        size_t key_start;
        size_t key_end;
        size_t value;
    };

    struct object_key_comparator
    {
        object_key_comparator(const char* object_data)
            : data(object_data)
        {
        }

        bool operator()(const object_key_record& lhs, const string& rhs) const {
            const size_t lhs_length = lhs.key_end - lhs.key_start;
            const size_t rhs_length = rhs.length();
            if (lhs_length < rhs_length) {
                return true;
            } else if (lhs_length > rhs_length) {
                return false;
            }
            return memcmp(data + lhs.key_start, rhs.data(), lhs_length) < 0;
        }

        bool operator()(const string& lhs, const object_key_record& rhs) const {
            return !(*this)(rhs, lhs);
        }

        bool operator()(const object_key_record& lhs, const
                object_key_record& rhs)
        {
            const size_t lhs_length = lhs.key_end - lhs.key_start;
            const size_t rhs_length = rhs.key_end - rhs.key_start;
            if (lhs_length < rhs_length) {
                return true;
            } else if (lhs_length > rhs_length) {
                return false;
            }
            return memcmp(data + lhs.key_start, data + rhs.key_start,
                    lhs_length) < 0;
        }

        const char* data;
    };

    class refcount {
    public:
        refcount()
            : pn(new size_t(1))
        {}

        refcount(const refcount& rc)
            : pn(rc.pn)
        {
            ++*pn;
        }

        ~refcount() {
            if (--*pn == 0) {
                delete pn;
            }
        }

        size_t count() const {
            return *pn;
        }

    private:
        size_t* pn;

        refcount& operator=(const refcount&) = delete;
    };

    class mutable_string_view {
    public:
        mutable_string_view()
            : length(0)
            , data(0)
        {}

        mutable_string_view(const literal& s)
            : length(s.length())
        {
            data = new char[length];
            memcpy(data, s.data(), length);
        }

        mutable_string_view(const string& s)
            : length(s.length())
        {
            data = new char[length];
            memcpy(data, s.data(), length);
        }

        ~mutable_string_view() {
            if (uses.count() == 1) {
                delete[] data;
            }
        }

        size_t get_length() const {
            return length;
        }

        char* get_data() const {
            return data;
        }

    private:
        refcount uses;
        size_t length;
        char* data;
    };

    union integer_storage {
        int i;
        size_t u;
    };
    // TODO: reinstate with c++03 implementation
    //static_assert(sizeof(integer_storage) == sizeof(size_t), "integer_storage must have same size as one structure slot");

    union double_storage {
        enum {
            word_length = sizeof(double) / sizeof(size_t)
        };

#if defined(_M_IX86) || defined(__i386__) || defined(_X86_)
        static double load(const size_t* location) {
            return *reinterpret_cast<const double*>(location);
        }
        static void store(size_t* location, double value) {
            *reinterpret_cast<double*>(location) = value;
        }
#else
        static double load(const size_t* location) {
            double_storage s;
            for (unsigned i = 0; i < double_storage::word_length; ++i) {
                s.u[i] = location[i];
            }
            return s.d;
        }

        static void store(size_t* location, double value) {
            double_storage ns;
            ns.d = value;

            for (int i = 0; i < ns.word_length; ++i) {
                location[i] = ns.u[i];
            }
        }

        double d;
        size_t u[word_length];
#endif
    };
    // TODO: reinstate with c++03 implementation
    //static_assert(sizeof(double_storage) == sizeof(double), "double_storage should have same size as double");

    class value {
    public:
        explicit value(type value_type, const size_t* payload, const char* text)
            : value_type(value_type)
            , payload(payload)
            , text(text)
        {}

        type get_type() const {
            return value_type;
        }

        // valid iff get_type() is TYPE_ARRAY or TYPE_OBJECT
        size_t get_length() const {
            assert_type_2(TYPE_ARRAY, TYPE_OBJECT);
            return payload[0];
        }

        // valid iff get_type() is TYPE_ARRAY
        value get_array_element(size_t index) const {
            assert_type(TYPE_ARRAY);
            size_t element = payload[1 + index];
            return value(get_element_type(element), payload + get_element_value(element), text);
        }

        // valid iff get_type() is TYPE_OBJECT
        string get_object_key(size_t index) const {
            assert_type(TYPE_OBJECT);
            const size_t* s = payload + 1 + index * 3;
            return string(text + s[0], s[1] - s[0]);
        }

        // valid iff get_type() is TYPE_OBJECT
        value get_object_value(size_t index) const {
            assert_type(TYPE_OBJECT);
            size_t element = payload[3 + index * 3];
            return value(get_element_type(element), payload + get_element_value(element), text);
        }


        // valid iff get_type() is TYPE_OBJECT
        // return get_length() if there is no such key
        size_t find_object_key(const string& key) const {
            assert_type(TYPE_OBJECT);
            const object_key_record* start = reinterpret_cast<const object_key_record*>(payload + 1);
            const object_key_record* end = start + get_length();
            const object_key_record* i = std::lower_bound(start, end, key, object_key_comparator(text));
            return (i != end
                    && (i->key_end - i->key_start) == key.length()
                    && memcmp(key.data(), text + i->key_start, key.length()) == 0)? i - start : get_length();
        }

        // valid iff get_type() is TYPE_INTEGER
        int get_integer_value() const {
            assert_type(TYPE_INTEGER);
            integer_storage s;
            s.u = payload[0];
            return s.i;
        }

        // valid iff get_type() is TYPE_DOUBLE
        double get_double_value() const {
            assert_type(TYPE_DOUBLE);
            return double_storage::load(payload);
        }

        // valid iff get_type() is TYPE_INTEGER or TYPE_DOUBLE
        double get_number_value() const {
            assert_type_2(TYPE_INTEGER, TYPE_DOUBLE);
            if (get_type() == TYPE_INTEGER) {
                return get_integer_value();
            } else {
                return get_double_value();
            }
        }

        // valid iff get_type() is TYPE_STRING
        size_t get_string_length() const {
            assert_type(TYPE_STRING);
            return payload[1] - payload[0];
        }

        // valid iff get_type() is TYPE_STRING
        std::string as_string() const {
            assert_type(TYPE_STRING);
            return std::string(text + payload[0], text + payload[1]);
        }

    private:
        void assert_type(type expected) const {
            assert(expected == get_type());
        }

        void assert_type_2(type e1, type e2) const {
            assert(e1 == get_type() || e2 == get_type());
        }

        const type value_type;
        const size_t* const payload;
        const char* const text;

    };

    class document {
    public:
        explicit document(mutable_string_view& input, const size_t* structure, type root_type, const size_t* root, size_t error_line, size_t error_column, const std::string& error_message)
            : input(input)
            , structure(structure)
            , root_type(root_type)
            , root(root)
            , error_line(error_line)
            , error_column(error_column)
            , error_message(error_message)
        {}

        document(const document&) = delete;
        void operator=(const document&) = delete;

        document(document&& rhs)
            : input(rhs.input)
            , structure(rhs.structure)
            , root_type(rhs.root_type)
            , root(rhs.root)
            , error_line(rhs.error_line)
            , error_column(rhs.error_column)
            , error_message(rhs.error_message)
        {
            rhs.structure = 0;
            // should rhs's fields be zeroed too?
        }

        ~document() {
            delete[] structure;
        }

        bool is_valid() const {
            return !!structure;
        }

        value get_root() const {
            return value(root_type, root, input.get_data());
        }

        size_t get_error_line() const {
            return error_line;
        }

        size_t get_error_column() const {
            return error_column;
        }

        std::string get_error_message() const {
            return error_message;
        }

    private:
        mutable_string_view input;
        const size_t* structure;
        const type root_type;
        const size_t* const root;
        const size_t error_line;
        const size_t error_column;
        const std::string error_message;
    };

    class parser {
    public:
        parser(const mutable_string_view& msv, size_t* structure)
            : input(msv)
            , input_end(input.get_data() + input.get_length())
            , structure(structure)
            , p(input.get_data())
            , temp(structure)
            , root_type(TYPE_NULL)
            , out(structure + input.get_length())
            , error_line(0)
            , error_column(0)
        {}

        document get_document() {
            if (parse()) {
                return document(input, structure, root_type, out, 0, 0, std::string());
            } else {
                delete[] structure;
                return document(input, 0, TYPE_NULL, 0, error_line, error_column, error_message);
            }
        }

    private:
        struct error_result {
            operator bool() const {
                return false;
            }
        };

        struct parse_result {
            parse_result(error_result)
                : success(false)
            {}

            parse_result(type t)
                : success(true)
                , value_type(t)
            {}

            bool operator!() const {
                return !success;
            }

            bool success;
            type value_type;
        };

        bool at_eof() {
            return p == input_end;
        }

        char peek_structure() {
            for (;;) {
                if (p == input_end) {
                    // 0 is never legal as a structural character in json text so treat it as eof
                    return 0;
                }
                switch (*p) {
                    case 0x20:
                    case 0x09:
                    case 0x0A:
                    case 0x0D:
                        ++p;
                        continue;
                    default:
                        return *p;
                }
            }
        }

        error_result error(const char* format, ...) {
            error_line = 1;
            error_column = 1;

            char* c = input.get_data();
            while (c < p) {
                if (*c == '\r') {
                    if (c + 1 < p && c[1] == '\n') {
                        ++error_line;
                        error_column = 1;
                        ++c;
                    } else {
                        ++error_line;
                        error_column = 1;
                    }
                } else if (*c == '\n') {
                    ++error_line;
                    error_column = 1;
                } else {
                    // TODO: count UTF-8 characters
                    ++error_column;
                }
                ++c;
            }


            char buf[1024];
            buf[1023] = 0;
            va_list ap;
            va_start(ap, format);
            vsnprintf(buf, 1023, format, ap);
            va_end(ap);

            error_message = buf;
            return error_result();
        }

        bool parse() {
            char c = peek_structure();
            if (c == 0) {
                return error("no root element");
            }

            type current_structure_type;
            if (c == '[') {
                current_structure_type = TYPE_ARRAY;
            } else if (c == '{') {
                current_structure_type = TYPE_OBJECT;
            } else {
                return error("document root must be object or array");
            }
            ++p;

            size_t* current_base = temp;
            *temp++ = make_element(current_structure_type, ROOT_MARKER);

            parse_result result = error_result();

            for (;;) {
                char closing_bracket = (current_structure_type == TYPE_OBJECT ? '}' : ']');

                c = peek_structure();
                if (temp > current_base + 1) {
                    if (c != closing_bracket) {
                        if (c == ',') {
                            ++p;
                            c = peek_structure();
                        } else {
                            return error("expected ,");
                        }
                    }
                }

                if (current_structure_type == TYPE_OBJECT && c != '}') {
                    if (c != '"') {
                        return error("object key must be quoted");
                    }
                    result = parse_string(temp);
                    if (!result) {
                        return error("invalid object key");
                    }
                    if (peek_structure() != ':') {
                        return error("expected :");
                    }
                    ++p;
                    temp += 2;
                }

                switch (peek_structure()) {
                    type next_type;
                    parse_result (parser::*structure_installer)(size_t* base);

                    case 0:
                        return error("unexpected end of input");
                    case 'n':
                        result = parse_null();
                        break;
                    case 'f':
                        result = parse_false();
                        break;
                    case 't':
                        result = parse_true();
                        break;
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                    case '-':
                        result = parse_number();
                        break;
                    case '"':
                        result = parse_string();
                        break;

                    case '[':
                        next_type = TYPE_ARRAY;
                        goto push;
                    case '{':
                        next_type = TYPE_OBJECT;
                        goto push;
                    push: {
                        ++p;
                        size_t* previous_base = current_base;
                        current_base = temp;
                        *temp++ = make_element(current_structure_type, previous_base - structure);
                        current_structure_type = next_type;
                        continue;
                    }

                    case ']':
                        if (current_structure_type == TYPE_ARRAY) {
                            structure_installer = &parser::install_array;
                            goto pop;
                        } else {
                            return error("expected }");
                        }
                    case '}':
                        if (current_structure_type == TYPE_OBJECT) {
                            structure_installer = &parser::install_object;
                            goto pop;
                        } else {
                            return error("expected ]");
                        }
                    pop: {
                        ++p;
                        size_t element = *current_base;
                        result = (this->*structure_installer)(current_base + 1);
                        size_t parent = get_element_value(element);
                        if (parent == ROOT_MARKER) {
                            root_type = result.value_type;
                            goto done;
                        }
                        temp = current_base;
                        current_base = structure + parent;
                        current_structure_type = get_element_type(element);
                        break;
                    }
                    default:
                        return error("cannot parse unknown value");
                }

                if (!result) {
                    return result.success;
                }

                *temp++ = make_element(result.value_type, out - current_base - 1);
            }

        done:
            if (0 == peek_structure()) {
                return true;
            } else {
                return error("expected end of input");
            }
        }

        bool has_remaining_characters(ptrdiff_t remaining) {
            return input_end - p >= remaining;
        }

        parse_result parse_null() {
            if (SAJSON_UNLIKELY(!has_remaining_characters(4))) {
                return error("unexpected end of input");
            }
            char p1 = p[1];
            char p2 = p[2];
            char p3 = p[3];
            if (SAJSON_UNLIKELY(p1 != 'u' || p2 != 'l' || p3 != 'l')) {
                return error("expected 'null'");
            }
            p += 4;
            return TYPE_NULL;
        }

        parse_result parse_false() {
            if (SAJSON_UNLIKELY(!has_remaining_characters(5))) {
                return error("unexpected end of input");
            }
            char p1 = p[1];
            char p2 = p[2];
            char p3 = p[3];
            char p4 = p[4];
            if (SAJSON_UNLIKELY(p1 != 'a' || p2 != 'l' || p3 != 's' || p4 != 'e')) {
                return error("expected 'false'");
            }
            p += 5;
            return TYPE_FALSE;
        }

        parse_result parse_true() {
            if (SAJSON_UNLIKELY(!has_remaining_characters(4))) {
                return error("unexpected end of input");
            }
            char p1 = p[1];
            char p2 = p[2];
            char p3 = p[3];
            if (SAJSON_UNLIKELY(p1 != 'r' || p2 != 'u' || p3 != 'e')) {
                return error("expected 'true'");
            }
            p += 4;
            return TYPE_TRUE;
        }

        static double pow10(int exponent) {
            if (exponent > 308) {
                return std::numeric_limits<double>::infinity();
            } else if (exponent < -323) {
                return 0.0;
            }
            static const double constants[] = {
                1e-323,1e-322,1e-321,1e-320,1e-319,1e-318,1e-317,1e-316,1e-315,1e-314,
                1e-313,1e-312,1e-311,1e-310,1e-309,1e-308,1e-307,1e-306,1e-305,1e-304,
                1e-303,1e-302,1e-301,1e-300,1e-299,1e-298,1e-297,1e-296,1e-295,1e-294,
                1e-293,1e-292,1e-291,1e-290,1e-289,1e-288,1e-287,1e-286,1e-285,1e-284,
                1e-283,1e-282,1e-281,1e-280,1e-279,1e-278,1e-277,1e-276,1e-275,1e-274,
                1e-273,1e-272,1e-271,1e-270,1e-269,1e-268,1e-267,1e-266,1e-265,1e-264,
                1e-263,1e-262,1e-261,1e-260,1e-259,1e-258,1e-257,1e-256,1e-255,1e-254,
                1e-253,1e-252,1e-251,1e-250,1e-249,1e-248,1e-247,1e-246,1e-245,1e-244,
                1e-243,1e-242,1e-241,1e-240,1e-239,1e-238,1e-237,1e-236,1e-235,1e-234,
                1e-233,1e-232,1e-231,1e-230,1e-229,1e-228,1e-227,1e-226,1e-225,1e-224,
                1e-223,1e-222,1e-221,1e-220,1e-219,1e-218,1e-217,1e-216,1e-215,1e-214,
                1e-213,1e-212,1e-211,1e-210,1e-209,1e-208,1e-207,1e-206,1e-205,1e-204,
                1e-203,1e-202,1e-201,1e-200,1e-199,1e-198,1e-197,1e-196,1e-195,1e-194,
                1e-193,1e-192,1e-191,1e-190,1e-189,1e-188,1e-187,1e-186,1e-185,1e-184,
                1e-183,1e-182,1e-181,1e-180,1e-179,1e-178,1e-177,1e-176,1e-175,1e-174,
                1e-173,1e-172,1e-171,1e-170,1e-169,1e-168,1e-167,1e-166,1e-165,1e-164,
                1e-163,1e-162,1e-161,1e-160,1e-159,1e-158,1e-157,1e-156,1e-155,1e-154,
                1e-153,1e-152,1e-151,1e-150,1e-149,1e-148,1e-147,1e-146,1e-145,1e-144,
                1e-143,1e-142,1e-141,1e-140,1e-139,1e-138,1e-137,1e-136,1e-135,1e-134,
                1e-133,1e-132,1e-131,1e-130,1e-129,1e-128,1e-127,1e-126,1e-125,1e-124,
                1e-123,1e-122,1e-121,1e-120,1e-119,1e-118,1e-117,1e-116,1e-115,1e-114,
                1e-113,1e-112,1e-111,1e-110,1e-109,1e-108,1e-107,1e-106,1e-105,1e-104,
                1e-103,1e-102,1e-101,1e-100,1e-99,1e-98,1e-97,1e-96,1e-95,1e-94,1e-93,
                1e-92,1e-91,1e-90,1e-89,1e-88,1e-87,1e-86,1e-85,1e-84,1e-83,1e-82,1e-81,
                1e-80,1e-79,1e-78,1e-77,1e-76,1e-75,1e-74,1e-73,1e-72,1e-71,1e-70,1e-69,
                1e-68,1e-67,1e-66,1e-65,1e-64,1e-63,1e-62,1e-61,1e-60,1e-59,1e-58,1e-57,
                1e-56,1e-55,1e-54,1e-53,1e-52,1e-51,1e-50,1e-49,1e-48,1e-47,1e-46,1e-45,
                1e-44,1e-43,1e-42,1e-41,1e-40,1e-39,1e-38,1e-37,1e-36,1e-35,1e-34,1e-33,
                1e-32,1e-31,1e-30,1e-29,1e-28,1e-27,1e-26,1e-25,1e-24,1e-23,1e-22,1e-21,
                1e-20,1e-19,1e-18,1e-17,1e-16,1e-15,1e-14,1e-13,1e-12,1e-11,1e-10,1e-9,
                1e-8,1e-7,1e-6,1e-5,1e-4,1e-3,1e-2,1e-1,1e0,1e1,1e2,1e3,1e4,1e5,1e6,1e7,
                1e8,1e9,1e10,1e11,1e12,1e13,1e14,1e15,1e16,1e17,1e18,1e19,1e20,1e21,
                1e22,1e23,1e24,1e25,1e26,1e27,1e28,1e29,1e30,1e31,1e32,1e33,1e34,1e35,
                1e36,1e37,1e38,1e39,1e40,1e41,1e42,1e43,1e44,1e45,1e46,1e47,1e48,1e49,
                1e50,1e51,1e52,1e53,1e54,1e55,1e56,1e57,1e58,1e59,1e60,1e61,1e62,1e63,
                1e64,1e65,1e66,1e67,1e68,1e69,1e70,1e71,1e72,1e73,1e74,1e75,1e76,1e77,
                1e78,1e79,1e80,1e81,1e82,1e83,1e84,1e85,1e86,1e87,1e88,1e89,1e90,1e91,
                1e92,1e93,1e94,1e95,1e96,1e97,1e98,1e99,1e100,1e101,1e102,1e103,1e104,
                1e105,1e106,1e107,1e108,1e109,1e110,1e111,1e112,1e113,1e114,1e115,1e116,
                1e117,1e118,1e119,1e120,1e121,1e122,1e123,1e124,1e125,1e126,1e127,1e128,
                1e129,1e130,1e131,1e132,1e133,1e134,1e135,1e136,1e137,1e138,1e139,1e140,
                1e141,1e142,1e143,1e144,1e145,1e146,1e147,1e148,1e149,1e150,1e151,1e152,
                1e153,1e154,1e155,1e156,1e157,1e158,1e159,1e160,1e161,1e162,1e163,1e164,
                1e165,1e166,1e167,1e168,1e169,1e170,1e171,1e172,1e173,1e174,1e175,1e176,
                1e177,1e178,1e179,1e180,1e181,1e182,1e183,1e184,1e185,1e186,1e187,1e188,
                1e189,1e190,1e191,1e192,1e193,1e194,1e195,1e196,1e197,1e198,1e199,1e200,
                1e201,1e202,1e203,1e204,1e205,1e206,1e207,1e208,1e209,1e210,1e211,1e212,
                1e213,1e214,1e215,1e216,1e217,1e218,1e219,1e220,1e221,1e222,1e223,1e224,
                1e225,1e226,1e227,1e228,1e229,1e230,1e231,1e232,1e233,1e234,1e235,1e236,
                1e237,1e238,1e239,1e240,1e241,1e242,1e243,1e244,1e245,1e246,1e247,1e248,
                1e249,1e250,1e251,1e252,1e253,1e254,1e255,1e256,1e257,1e258,1e259,1e260,
                1e261,1e262,1e263,1e264,1e265,1e266,1e267,1e268,1e269,1e270,1e271,1e272,
                1e273,1e274,1e275,1e276,1e277,1e278,1e279,1e280,1e281,1e282,1e283,1e284,
                1e285,1e286,1e287,1e288,1e289,1e290,1e291,1e292,1e293,1e294,1e295,1e296,
                1e297,1e298,1e299,1e300,1e301,1e302,1e303,1e304,1e305,1e306,1e307,1e308
            };
            return constants[exponent + 323];
        }

        parse_result parse_number() {
            bool negative = false;
            if ('-' == *p) {
                ++p;
                negative = true;

                if (at_eof()) {
                    return error("unexpected end of input");
                }
            }

            bool try_double = false;

            int i = 0;
            double d = 0.0; // gcc complains that d might be used uninitialized which isn't true. appease the warning anyway.
            for (;;) {
                char c = *p;
                if (c < '0' || c > '9') {
                    break;
                }

                ++p;
                if (at_eof()) {
                    return error("unexpected end of input");
                }

                char digit = c - '0';

                if (SAJSON_UNLIKELY(!try_double && i > INT_MAX / 10 - 9)) {
                    // TODO: could split this into two loops
                    try_double = true;
                    d = i;
                }
                if (SAJSON_UNLIKELY(try_double)) {
                    d = 10.0 * d + digit;
                } else {
                    i = 10 * i + digit;
                }
            }

            int exponent = 0;

            if ('.' == *p) {
                if (!try_double) {
                    try_double = true;
                    d = i;
                }
                ++p;
                if (at_eof()) {
                    return error("unexpected end of input");
                }
                for (;;) {
                    char c = *p;
                    if (c < '0' || c > '9') {
                        break;
                    }

                    ++p;
                    if (at_eof()) {
                        return error("unexpected end of input");
                    }
                    d = d * 10 + (c - '0');
                    --exponent;
                }
            }

            char e = *p;
            if ('e' == e || 'E' == e) {
                if (!try_double) {
                    try_double = true;
                    d = i;
                }
                ++p;
                if (at_eof()) {
                    return error("unexpected end of input");
                }

                bool negativeExponent = false;
                if ('-' == *p) {
                    ++p;
                    negativeExponent = true;
                    if (at_eof()) {
                        return error("unexpected end of input");
                    }
                } else if ('+' == *p) {
                    ++p;
                    if (at_eof()) {
                        return error("unexpected end of input");
                    }
                }

                int exp = 0;
                for (;;) {
                    char c = *p;
                    if (c < '0' || c > '9') {
                        break;
                    }

                    ++p;
                    if (at_eof()) {
                        return error("unexpected end of input");
                    }

                    exp = 10 * exp + (c - '0');
                }
                exponent += (negativeExponent ? -exp : exp);
            }

            if (exponent) {
                assert(try_double);
                d *= pow10(exponent);
            }

            if (negative) {
                if (try_double) {
                    d = -d;
                } else {
                    i = -i;
                }
            }
            if (try_double) {
                out -= double_storage::word_length;
                double_storage::store(out, d);
                return TYPE_DOUBLE;
            } else {
                integer_storage is;
                is.i = i;

                *--out = is.u;
                return TYPE_INTEGER;
            }
        }

        parse_result install_array(size_t* array_base) {
            const size_t length = temp - array_base;
            size_t* const new_base = out - length - 1;
            while (temp > array_base) {
                // I think this addition is legal because the tag bits are at the top?
                *(--out) = *(--temp) + (array_base - new_base);
            }
            *(--out) = length;

            return TYPE_ARRAY;
        }

        parse_result install_object(size_t* object_base) {
            const size_t length = (temp - object_base) / 3;
            object_key_record* oir = reinterpret_cast<object_key_record*>(object_base);
            std::sort(
                oir,
                oir + length,
                object_key_comparator(input.get_data()));

            size_t* const new_base = out - length * 3 - 1;
            size_t i = length;
            while (i--) {
                // I think this addition is legal because the tag bits are at the top?
                *(--out) = *(--temp) + (object_base - new_base);
                *(--out) = *(--temp);
                *(--out) = *(--temp);
            }
            *(--out) = length;

            return TYPE_OBJECT;
        }

        parse_result parse_string(size_t* tag = 0) {
            if (!tag) {
                out -= 2;
                tag = out;
            }

            ++p; // "
            size_t start = p - input.get_data();
            for (;;) {
                if (SAJSON_UNLIKELY(p >= input_end)) {
                    return error("unexpected end of input");
                }

                if (SAJSON_UNLIKELY(*p >= 0 && *p < 0x20)) {
                    return error("illegal unprintable codepoint in string: %d", static_cast<int>(*p));
                }

                switch (*p) {
                    case '"':
                        tag[0] = start;
                        tag[1] = p - input.get_data();
                        ++p;
                        return TYPE_STRING;

                    case '\\':
                        return parse_string_slow(tag, start);

                    default:
                        ++p;
                        break;
                }
            }
        }

        parse_result read_hex(unsigned& u) {
            unsigned v = 0;
            int i = 4;
            while (i--) {
                unsigned char c = *p++;
                if (c >= '0' && c <= '9') {
                    c -= '0';
                } else if (c >= 'a' && c <= 'f') {
                    c = c - 'a' + 10;
                } else if (c >= 'A' && c <= 'F') {
                    c = c - 'A' + 10;
                } else {
                    return error("invalid character in unicode escape");
                }
                v = (v << 4) + c;
            }

            u = v;
            return TYPE_NULL; // ???
        }

        void write_utf8(unsigned codepoint, char*& end) {
            if (codepoint < 0x80) {
                *end++ = codepoint;
            } else if (codepoint < 0x800) {
                *end++ = 0xC0 | (codepoint >> 6);
                *end++ = 0x80 | (codepoint & 0x3F);
            } else if (codepoint < 0x10000) {
                *end++ = 0xE0 | (codepoint >> 12);
                *end++ = 0x80 | ((codepoint >> 6) & 0x3F);
                *end++ = 0x80 | (codepoint & 0x3F);
            } else {
                assert(codepoint < 0x200000);
                *end++ = 0xF0 | (codepoint >> 18);
                *end++ = 0x80 | ((codepoint >> 12) & 0x3F);
                *end++ = 0x80 | ((codepoint >> 6) & 0x3F);
                *end++ = 0x80 | (codepoint & 0x3F);
            }
        }

        parse_result parse_string_slow(size_t* tag, size_t start) {
            char* end = p;

            for (;;) {
                if (SAJSON_UNLIKELY(p >= input_end)) {
                    return error("unexpected end of input");
                }

                if (SAJSON_UNLIKELY(*p >= 0 && *p < 0x20)) {
                    return error("illegal unprintable codepoint in string: %d", static_cast<int>(*p));
                }

                switch (*p) {
                    case '"':
                        tag[0] = start;
                        tag[1] = end - input.get_data();
                        ++p;
                        return TYPE_STRING;

                    case '\\':
                        ++p;
                        if (SAJSON_UNLIKELY(p >= input_end)) {
                            return error("unexpected end of input");
                        }

                        char replacement;
                        switch (*p) {
                            case '"': replacement = '"'; goto replace;
                            case '\\': replacement = '\\'; goto replace;
                            case '/': replacement = '/'; goto replace;
                            case 'b': replacement = '\b'; goto replace;
                            case 'f': replacement = '\f'; goto replace;
                            case 'n': replacement = '\n'; goto replace;
                            case 'r': replacement = '\r'; goto replace;
                            case 't': replacement = '\t'; goto replace;
                            replace:
                                *end++ = replacement;
                                ++p;
                                break;
                            case 'u': {
                                ++p;
                                if (SAJSON_UNLIKELY(!has_remaining_characters(4))) {
                                    return error("unexpected end of input");
                                }
                                unsigned u = 0; // gcc's complaining that this could be used uninitialized. wrong.
                                parse_result result = read_hex(u);
                                if (!result) {
                                    return result;
                                }
                                if (u >= 0xD800 && u <= 0xDBFF) {
                                    if (SAJSON_UNLIKELY(!has_remaining_characters(6))) {
                                        return error("unexpected end of input during UTF-16 surrogate pair");
                                    }
                                    char p0 = p[0];
                                    char p1 = p[1];
                                    if (p0 != '\\' || p1 != 'u') {
                                        return error("expected \\u");
                                    }
                                    p += 2;
                                    unsigned v = 0; // gcc's complaining that this could be used uninitialized. wrong.
                                    result = read_hex(v);
                                    if (!result) {
                                        return result;
                                    }

                                    if (v < 0xDC00 || v > 0xDFFF) {
                                        return error("invalid UTF-16 trail surrogate");
                                    }
                                    u = 0x10000 + (((u - 0xD800) << 10) | (v - 0xDC00));
                                }
                                write_utf8(u, end);
                                break;
                            }
                            default:
                                return error("unknown escape");
                        }
                        break;

                    default:
                        *end++ = *p++;
                        break;
                }
            }
        }

        mutable_string_view input;
        char* const input_end;
        size_t* const structure;

        char* p;
        size_t* temp;
        type root_type;
        size_t* out;
        size_t error_line;
        size_t error_column;
        std::string error_message;
    };

    template<typename StringType>
    document parse(const StringType& string) {
        mutable_string_view ms(string);

        size_t length = string.length();
        size_t* structure = new size_t[length];

        return parser(ms, structure).get_document();
    }
}

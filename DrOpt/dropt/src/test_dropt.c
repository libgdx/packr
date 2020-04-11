/** test_dropt.c
  *
  * Unit tests for dropt.
  *
  * Copyright (c) 2007-2012 James D. Lin <jameslin@cal.berkeley.edu>
  *
  * This software is provided 'as-is', without any express or implied
  * warranty.  In no event will the authors be held liable for any damages
  * arising from the use of this software.
  *
  * Permission is granted to anyone to use this software for any purpose,
  * including commercial applications, and to alter it and redistribute it
  * freely, subject to the following restrictions:
  *
  * 1. The origin of this software must not be misrepresented; you must not
  *    claim that you wrote the original software. If you use this software
  *    in a product, an acknowledgment in the product documentation would be
  *    appreciated but is not required.
  * 2. Altered source versions must be plainly marked as such, and must not be
  *    misrepresented as being the original software.
  * 3. This notice may not be removed or altered from any source distribution.
  */

#ifdef _MSC_VER
#define _CRT_SECURE_NO_DEPRECATE
#endif

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <wctype.h>
#include <float.h>
#include <assert.h>

#include "dropt.h"
#include "dropt_string.h"

/* Compatibility junk. */
#ifdef DROPT_USE_WCHAR
    #define ftprintf fwprintf
    #define fputts fputws
    #define fputtc fputwc

    #define tcsncat wcsncat
    #define stscanf swscanf

    #define istdigit(wc) iswdigit(wc)
#else
    #define ftprintf fprintf
    #define fputts fputs
    #define fputtc fputc

    #define tcsncat strncat
    #define stscanf sscanf

    #define istdigit(c) isdigit((unsigned char) c)
#endif

/* For convenience. */
#define T(s) DROPT_TEXT_LITERAL(s)

#ifndef ARRAY_LENGTH
#define ARRAY_LENGTH(array) (sizeof (array) / sizeof (array)[0])
#endif

#ifndef ABS
#define ABS(x) (((x) < 0) ? -(x) : (x))
#endif

#ifndef MAX
#define MAX(x, y) (((x) > (y)) ? (x) : (y))
#endif

#define ZERO_MEMORY(p, numBytes) memset(p, 0, numBytes)


enum
{
    my_dropt_error_bad_ip_address = dropt_error_custom_start
};

typedef enum { false, true } bool;

static dropt_bool showHelp;
static dropt_bool quiet;
static dropt_bool allowConcatenatedArgs;
static dropt_bool normalFlag;
static dropt_bool requiredArgFlag;
static dropt_bool hiddenFlag;
static dropt_char* stringVal;
static dropt_char* stringVal2;
static int intVal;

typedef struct
{
    bool is_set;
    unsigned int value;
} optional_uint;

static optional_uint optionalUInt;

static unsigned int ipAddress;


static void
init_option_defaults(void)
{
    showHelp = false;
    quiet = false;
    allowConcatenatedArgs = false;
    normalFlag = false;
    requiredArgFlag = false;
    hiddenFlag = false;
    stringVal = NULL;
    stringVal2 = NULL;
    intVal = 0;

    optionalUInt.is_set = false;
    optionalUInt.value = 10;

    ipAddress = 0;
}


static dropt_error
handle_optional_uint(dropt_context* context, const dropt_char* optionArgument, void* handlerData)
{
    dropt_error err = dropt_error_none;

    if (handlerData == NULL)
    {
        DROPT_MISUSE("No handler data specified.");
        err = dropt_error_bad_configuration;
    }
    else
    {
        optional_uint* p = handlerData;

        if (optionArgument != NULL)
        {
            err = dropt_handle_uint(context, optionArgument, &(p->value));
        }

        if (err == dropt_error_none) { p->is_set = true; }
    }

    return err;
}


static dropt_error
handle_ip_address(dropt_context* context, const dropt_char* optionArgument, void* handlerData)
{
    dropt_error err = dropt_error_none;
    unsigned int octet[4];
    size_t i;

    unsigned int* out = handlerData;

    assert(out != NULL);

    if (optionArgument == NULL || optionArgument[0] == T('\0'))
    {
        err = dropt_error_insufficient_arguments;
        goto exit;
    }

    {
        const dropt_char* p = optionArgument;
        while (*p != T('\0'))
        {
            if (!istdigit(*p) && *p != T('.'))
            {
                err = my_dropt_error_bad_ip_address;
                goto exit;
            }
            p++;
        }
    }

    if (stscanf(optionArgument, T("%u.%u.%u.%u"), &octet[0], &octet[1], &octet[2], &octet[3])
        != ARRAY_LENGTH(octet))
    {
        err = my_dropt_error_bad_ip_address;
        goto exit;
    }

    for (i = 0; i < ARRAY_LENGTH(octet); i++)
    {
        if (octet[i] > 0xFF)
        {
            err = my_dropt_error_bad_ip_address;
            goto exit;
        }
    }

    *out = (octet[0] << 24) | (octet[1] << 16) | (octet[2] << 8) | octet[3];

exit:
    return err;
}


dropt_char*
safe_strncat(dropt_char* dest, size_t destSize, const dropt_char* s)
{
    assert(dest != NULL);
    assert(s != NULL);
    return (destSize == 0)
           ? dest
           : tcsncat(dest, s, destSize - dropt_strlen(dest) - 1);
}


static dropt_char*
my_dropt_error_handler(dropt_error error, const dropt_char* optionName,
                       const dropt_char* optionArgument, void* handlerData)
{
#ifdef DROPT_NO_STRING_BUFFERS
    if (error == my_dropt_error_bad_ip_address)
    {
        return dropt_strdup(T("Invalid IP address"));
    }
    else
    {
        /* This is inefficient, but it's not important here. */
        dropt_char buf[256] = T("Failed on: ");
        safe_strncat(buf, ARRAY_LENGTH(buf), optionName);
        safe_strncat(buf, ARRAY_LENGTH(buf), T("="));
        safe_strncat(buf, ARRAY_LENGTH(buf), optionArgument ? optionArgument
                                                            : T("(null)"));
        return dropt_strdup(buf);
    }
#else
    if (error == my_dropt_error_bad_ip_address)
    {
        return dropt_asprintf(T("Invalid IP address for option %s: %s"), optionName, optionArgument);
    }
    else
    {
        return dropt_default_error_handler(error, optionName, optionArgument);
    }
#endif
}


dropt_option options[] = {
    { T('\0'), NULL, T("Main options:") },
    { T('h'),  T("help"), T("Shows help."), NULL, dropt_handle_bool, &showHelp, dropt_attr_halt },
    { T('?'),  NULL, NULL, NULL, dropt_handle_bool, &showHelp, dropt_attr_halt },
    { T('q'),  T("quiet"), T("Quiet mode."), NULL, dropt_handle_bool, &quiet },
    { T('c'),  T("concatenated"), T("Allow concatenated arguments."), NULL, dropt_handle_bool, &allowConcatenatedArgs },
    { T('n'),  T("normalFlag"), T("A normal flag."), NULL, dropt_handle_bool, &normalFlag },
    { T('r'),  T("requiredArgFlag"), T("A flag with a required argument."), T("bool"), dropt_handle_verbose_bool, &requiredArgFlag },
    { T('H'),  T("hiddenFlag"), T("This is hidden."), NULL, dropt_handle_bool, &hiddenFlag, dropt_attr_hidden },
    { T('s'),  T("string"), T("Test string value."), T("value"), dropt_handle_string, &stringVal },
    { T('S'),  T("string2"), T("Test string value."), T("value"), dropt_handle_string, &stringVal2 },
    { T('i'),  T("int"), T("Test integer value."), T("value"), dropt_handle_int, &intVal },
    { T('\0'), NULL, T("") },
    { T('\0'), NULL, T("Options for testing custom handlers:") },
    { T('o'),  T("optionalUInt"), T("Test an optional unsigned integer argument.\nAlso test multiple\nlines."), T("lines"), handle_optional_uint, &optionalUInt, dropt_attr_optional_val },
    { T('\0'), T("ip"), T("Test IP address."), T("address"), handle_ip_address, &ipAddress},
    { 0 }
};



static bool
integer_equal(long int a, long int b)
{
    return a == b;
}


static bool
double_equal(double a, double b)
{
    /* Based on code from <http://c-faq.com/fp/fpequal.html>. */
    double a0 = ABS(a);
    double b0 = ABS(b);
    double d = a - b;
    return ABS(d) <= DBL_EPSILON * MAX(a0, b0);
}


static bool
string_equal(const dropt_char* a, const dropt_char* b)
{
    if (a == NULL || b == NULL)
    {
        return a == b;
    }
    else
    {
        return dropt_strcmp(a, b) == 0;
    }
}


#define VERIFY(expr) verify(expr, #expr, __LINE__)
static bool
verify(bool b, const char* s, unsigned int line)
{
    if (!b) { fprintf(stderr, "FAILED: %s (line: %u)\n", s, line); }
    return b;
}


static bool
test_strings(void)
{
#ifdef DROPT_NO_STRING_BUFFERS
    return true;
#else
    bool success = true;

    {
        const dropt_char* s = T("foo bar");
        const dropt_char* t = T("FOO QUX");

        dropt_char* copy;

        copy = dropt_strndup(s, 3);
        if (copy == NULL)
        {
            fputts(T("Insufficient memory.\n"), stderr);
            success = false;
            goto exit;
        }

        success &= VERIFY(dropt_strcmp(copy, T("foo")) == 0);
        free(copy);
        copy = NULL;

        copy = dropt_strndup(s, 100);
        if (copy == NULL)
        {
            fputts(T("Insufficient memory.\n"), stderr);
            success = false;
            goto exit;
        }

        success &= VERIFY(dropt_strcmp(copy, s) == 0);
        free(copy);
        copy = NULL;

        copy = dropt_strdup(s);
        if (copy == NULL)
        {
            fputts(T("Insufficient memory.\n"), stderr);
            success = false;
            goto exit;
        }

        success &= VERIFY(dropt_strcmp(copy, s) == 0);
        free(copy);
        copy = NULL;

        success &= VERIFY(dropt_strnicmp(s, t, 4) == 0);
        success &= VERIFY(dropt_strnicmp(s, t, 5) < 0);
        success &= VERIFY(dropt_strnicmp(t, s, 5) > 0);

        success &= VERIFY(dropt_stricmp(s, t) < 0);
        success &= VERIFY(dropt_stricmp(t, s) > 0);
        success &= VERIFY(dropt_stricmp(T("foo"), T("FOO")) == 0);
    }

    {
        dropt_char buf[4];

        ZERO_MEMORY(buf, sizeof buf);
        success &= VERIFY(dropt_snprintf(buf, ARRAY_LENGTH(buf), T("%s"), T("foo")) == 3);
        success &= VERIFY(string_equal(buf, T("foo")));

        ZERO_MEMORY(buf, sizeof buf);
        success &= VERIFY(dropt_snprintf(buf, ARRAY_LENGTH(buf), T("%s"), T("bar baz")) == 7);
        success &= VERIFY(string_equal(buf, T("bar")));
    }

    {
        dropt_char* expectedString = NULL;

        dropt_char* s;
        dropt_stringstream* ss = dropt_ssopen();
        if (ss == NULL)
        {
            fputts(T("Insufficient memory.\n"), stderr);
            success = false;
            goto exit;
        }

        success &= VERIFY(dropt_ssgetstring(ss)[0] == T('\0'));

        success &= VERIFY(dropt_ssprintf(ss, T("hello %s %X %d%c"),
                                         T("world"), 0xCAFEBABE, 31337, T('!')) == 27);

        dropt_ssprintf(ss, T("%c"), T('\n'));

        /* About 300 characters to make sure we overflow the default buffer
         * of 256 characters.
         */
        dropt_ssprintf(ss, T("Lorem ipsum dolor sit amet, consectetuer adipiscing elit. "));
        dropt_ssprintf(ss, T("Aenean quis mauris. In augue. "));
        dropt_ssprintf(ss, T("Suspendisse orci felis, tristique eget, lacinia rhoncus, interdum at, lorem."));
        dropt_ssprintf(ss, T("Aliquam gravida dui nec erat. Integer pede. Aliquam erat volutpat."));
        dropt_ssprintf(ss, T("In eu nisl. Curabitur non tellus id arcu feugiat porta orci aliquam."));

        success &= VERIFY(dropt_ssprintf(ss, T("%c"), T('\0')) == 1);
        dropt_ssprintf(ss, T("This is junk data."));

        s = dropt_ssfinalize(ss);

        expectedString = T("hello world CAFEBABE 31337!\n")
                         T("Lorem ipsum dolor sit amet, consectetuer adipiscing elit. ")
                         T("Aenean quis mauris. In augue. ")
                         T("Suspendisse orci felis, tristique eget, lacinia rhoncus, interdum at, lorem.")
                         T("Aliquam gravida dui nec erat. Integer pede. Aliquam erat volutpat.")
                         T("In eu nisl. Curabitur non tellus id arcu feugiat porta orci aliquam.");
        success &= VERIFY(string_equal(s, expectedString));
        free(s);
    }

exit:
    return success;
#endif
}


#define MAKE_TEST_FOR_HANDLER(handler, type, valueEqualityFunc, formatSpecifier) \
static bool \
test_ ## handler(dropt_context* context, const dropt_char* optionArgument, \
                 dropt_error expectedError, type expectedValue, type initValue, \
                 unsigned int line) \
{ \
    bool success = false; \
    type value = initValue; \
    dropt_error error = handler(context, optionArgument, &value); \
    if (error == expectedError && valueEqualityFunc(value, expectedValue)) \
    { \
        success = true; \
    } \
    else \
    { \
        const dropt_char* quote = optionArgument ? T("\"") : T(""); \
        ftprintf(stderr, \
                 T("FAILED: %s(%s%s%s) ") \
                 T("returned %d, expected %d.  ") \
                 T("Output ") formatSpecifier T(", expected ") formatSpecifier T(". (line: %u)\n"), \
                 T(#handler), quote, optionArgument ? optionArgument : T("NULL"), quote, \
                 error, expectedError, \
                 value, expectedValue, \
                 line); \
    } \
    return success; \
}


MAKE_TEST_FOR_HANDLER(dropt_handle_bool, dropt_bool, integer_equal, T("%d"))
MAKE_TEST_FOR_HANDLER(dropt_handle_verbose_bool, dropt_bool, integer_equal, T("%d"))
MAKE_TEST_FOR_HANDLER(dropt_handle_int, int, integer_equal, T("%d"))
MAKE_TEST_FOR_HANDLER(dropt_handle_uint, unsigned int, integer_equal, T("%u"))
MAKE_TEST_FOR_HANDLER(dropt_handle_double, double, double_equal, T("%g"))
MAKE_TEST_FOR_HANDLER(dropt_handle_string, dropt_char*, string_equal, T("%s"))


#define TEST_HANDLER(type, context, optionArgument, expectedError, expectedValue, initValue) \
    test_dropt_handle_ ## type( \
        context, optionArgument, expectedError, expectedValue, initValue, __LINE__)


static bool
test_dropt_handlers(dropt_context* context)
{
    bool success = true;

    const int i = 42;
    const unsigned int u = 0xCAFEBABE;
    const double d = 2.71828;

#if 0
    /* These tests normally aren't enabled because they intentionally
     * trigger DROPT_MISUSE, and that either generates error spew or is
     * fatal.
     */
    success &= (dropt_handle_bool(context, "1", NULL) == dropt_error_bad_configuration);
    success &= (dropt_handle_verbose_bool(context, "1", NULL) == dropt_error_bad_configuration);
    success &= (dropt_handle_int(context, "1", NULL) == dropt_error_bad_configuration);
    success &= (dropt_handle_uint(context, "1", NULL) == dropt_error_bad_configuration);
    success &= (dropt_handle_double(context, "1", NULL) == dropt_error_bad_configuration);
    success &= (dropt_handle_string(context, "1", NULL) == dropt_error_bad_configuration);
#endif

    success &= TEST_HANDLER(bool, context, NULL, dropt_error_none, 1, 0);
    success &= TEST_HANDLER(bool, context, NULL, dropt_error_none, 1, 0);
    success &= TEST_HANDLER(bool, context, T(""), dropt_error_mismatch, 0, 0);
    success &= TEST_HANDLER(bool, context, T(" "), dropt_error_mismatch, 0, 0);
    success &= TEST_HANDLER(bool, context, T("1"), dropt_error_none, 1, 0);
    success &= TEST_HANDLER(bool, context, T("0"), dropt_error_none, 0, 0);
    success &= TEST_HANDLER(bool, context, T("2"), dropt_error_mismatch, 0, 0);
    success &= TEST_HANDLER(bool, context, T("-1"), dropt_error_mismatch, 0, 0);
    success &= TEST_HANDLER(bool, context, T("01"), dropt_error_none, 1, 0);
    success &= TEST_HANDLER(bool, context, T("11"), dropt_error_mismatch, 0, 0);
    success &= TEST_HANDLER(bool, context, T("a"), dropt_error_mismatch, 0, 0);
    success &= TEST_HANDLER(bool, context, T("a"), dropt_error_mismatch, 1, 1);
    success &= TEST_HANDLER(bool, context, T("true"), dropt_error_mismatch, 0, 0);
    success &= TEST_HANDLER(bool, context, T("false"), dropt_error_mismatch, 0, 0);
    success &= TEST_HANDLER(bool, context, T("3000000000"), dropt_error_mismatch, 0, 0);
    success &= TEST_HANDLER(bool, context, T("-3000000000"), dropt_error_mismatch, 0, 0);
    success &= TEST_HANDLER(bool, context, T("5000000000"), dropt_error_mismatch, 0, 0);

    success &= TEST_HANDLER(verbose_bool, context, NULL, dropt_error_none, 1, 0);
    success &= TEST_HANDLER(verbose_bool, context, T(""), dropt_error_mismatch, 0, 0);
    success &= TEST_HANDLER(verbose_bool, context, T(" "), dropt_error_mismatch, 0, 0);
    success &= TEST_HANDLER(verbose_bool, context, T("1"), dropt_error_none, 1, 0);
    success &= TEST_HANDLER(verbose_bool, context, T("0"), dropt_error_none, 0, 0);
    success &= TEST_HANDLER(verbose_bool, context, T("2"), dropt_error_mismatch, 0, 0);
    success &= TEST_HANDLER(verbose_bool, context, T("-1"), dropt_error_mismatch, 0, 0);
    success &= TEST_HANDLER(verbose_bool, context, T("01"), dropt_error_none, 1, 0);
    success &= TEST_HANDLER(verbose_bool, context, T("11"), dropt_error_mismatch, 0, 0);
    success &= TEST_HANDLER(verbose_bool, context, T("a"), dropt_error_mismatch, 0, 0);
    success &= TEST_HANDLER(verbose_bool, context, T("a"), dropt_error_mismatch, 1, 1);
    success &= TEST_HANDLER(verbose_bool, context, T("true"), dropt_error_none, 1, 0);
    success &= TEST_HANDLER(verbose_bool, context, T("false"), dropt_error_none, 0, 0);

    success &= TEST_HANDLER(int, context, NULL, dropt_error_insufficient_arguments, i, i);
    success &= TEST_HANDLER(int, context, T(""), dropt_error_insufficient_arguments, i, i);
    success &= TEST_HANDLER(int, context, T(" "), dropt_error_mismatch, i, i);
    success &= TEST_HANDLER(int, context, T("0"), dropt_error_none, 0, 0);
    success &= TEST_HANDLER(int, context, T("-0"), dropt_error_none, 0, 0);
    success &= TEST_HANDLER(int, context, T("123"), dropt_error_none, 123, 0);
    success &= TEST_HANDLER(int, context, T("0123"), dropt_error_none, 123, 0);
    success &= TEST_HANDLER(int, context, T("+123"), dropt_error_none, 123, 0);
    success &= TEST_HANDLER(int, context, T("-123"), dropt_error_none, -123, 0);
    success &= TEST_HANDLER(int, context, T("12.3"), dropt_error_mismatch, i, i);
    success &= TEST_HANDLER(int, context, T("a"), dropt_error_mismatch, i, i);
    success &= TEST_HANDLER(int, context, T("123a"), dropt_error_mismatch, i, i);
    success &= TEST_HANDLER(int, context, T("3000000000"), dropt_error_overflow, i, i);
    success &= TEST_HANDLER(int, context, T("-3000000000"), dropt_error_overflow, i, i);

    success &= TEST_HANDLER(uint, context, NULL, dropt_error_insufficient_arguments, u, u);
    success &= TEST_HANDLER(uint, context, T(""), dropt_error_insufficient_arguments, u, u);
    success &= TEST_HANDLER(uint, context, T(" "), dropt_error_mismatch, u, u);
    success &= TEST_HANDLER(uint, context, T("0"), dropt_error_none, 0, 0);
    success &= TEST_HANDLER(uint, context, T("-0"), dropt_error_mismatch, u, u);
    success &= TEST_HANDLER(uint, context, T("123"), dropt_error_none, 123, 0);
    success &= TEST_HANDLER(uint, context, T("0123"), dropt_error_none, 123, 0);
    success &= TEST_HANDLER(uint, context, T("123"), dropt_error_none, 123, 0);
    success &= TEST_HANDLER(uint, context, T("+123"), dropt_error_none, 123, 0);
    success &= TEST_HANDLER(uint, context, T("-123"), dropt_error_mismatch, u, u);
    success &= TEST_HANDLER(uint, context, T("12.3"), dropt_error_mismatch, u, u);
    success &= TEST_HANDLER(uint, context, T("a"), dropt_error_mismatch, u, u);
    success &= TEST_HANDLER(uint, context, T("123a"), dropt_error_mismatch, u, u);
    success &= TEST_HANDLER(uint, context, T("3000000000"), dropt_error_none, 3000000000u, 0);
    success &= TEST_HANDLER(uint, context, T("-3000000000"), dropt_error_mismatch, u, u);
    success &= TEST_HANDLER(uint, context, T("5000000000"), dropt_error_overflow, u, u);

    success &= TEST_HANDLER(double, context, NULL, dropt_error_insufficient_arguments, d, d);
    success &= TEST_HANDLER(double, context, T(""), dropt_error_insufficient_arguments, d, d);
    success &= TEST_HANDLER(double, context, T(" "), dropt_error_mismatch, d, d);
    success &= TEST_HANDLER(double, context, T("123"), dropt_error_none, 123, 0);
    success &= TEST_HANDLER(double, context, T("0123"), dropt_error_none, 123, 0);
    success &= TEST_HANDLER(double, context, T("+123"), dropt_error_none, 123, 0);
    success &= TEST_HANDLER(double, context, T("-123"), dropt_error_none, -123, 0);
    success &= TEST_HANDLER(double, context, T("12.3"), dropt_error_none, 12.3, 0);
    success &= TEST_HANDLER(double, context, T(".123"), dropt_error_none, 0.123, 0);
    success &= TEST_HANDLER(double, context, T("123e-1"), dropt_error_none, 12.3, 0);
    success &= TEST_HANDLER(double, context, T("12.3e-1"), dropt_error_none, 1.23, 0);
    success &= TEST_HANDLER(double, context, T("a"), dropt_error_mismatch, d, d);
    success &= TEST_HANDLER(double, context, T("123a"), dropt_error_mismatch, d, d);
    success &= TEST_HANDLER(double, context, T("1e1024"), dropt_error_overflow, d, d);

    /* This test depends on implementation-dependent behavior of strtod, so
     * we're less strict.
     */
    {
        const dropt_char* s = T("1e-1024");
        double value = d;
        dropt_error error = dropt_handle_double(context, s, &value);
        if (!(   (error == dropt_error_underflow && value == d)
              || (error == dropt_error_none && value == 0)))
        {
            ftprintf(stderr,
                     T("FAILED: dropt_handle_double(\"%s\") ")
                     T("returned %d and output %g.  ")
                     T("Expected (%d, %g) or (%d, %g).\n"),
                     s,
                     error, value,
                     dropt_error_underflow, d,
                     dropt_error_none, 0.0);
            success = false;
        }
    }

    success &= TEST_HANDLER(string, context, NULL, dropt_error_insufficient_arguments, T("qux"), T("qux"));
    success &= TEST_HANDLER(string, context, T(""), dropt_error_none, T(""), NULL);
    success &= TEST_HANDLER(string, context, T(" "), dropt_error_none, T(" "), NULL);
    success &= TEST_HANDLER(string, context, T("foo"), dropt_error_none, T("foo"), NULL);
    success &= TEST_HANDLER(string, context, T("foo bar"), dropt_error_none, T("foo bar"), NULL);

    return success;
}


static dropt_error
get_and_print_dropt_error(dropt_context* context)
{
    dropt_error error = dropt_get_error(context);
    if (error != dropt_error_none)
    {
        ftprintf(stderr, T("[%d] %s\n"), error, dropt_get_error_message(context));
        dropt_clear_error(context);
    }
    return error;
}


static bool
test_dropt_parse(dropt_context* context)
{
    bool success = true;
    dropt_char** rest;

    /* Basic test for boolean options. */
    {
        dropt_char* args[] = { T("-n"), T("--hiddenFlag"), NULL };
        normalFlag = false;
        hiddenFlag = false;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(dropt_get_error(context) == dropt_error_none);
        success &= VERIFY(normalFlag == true);
        success &= VERIFY(hiddenFlag == true);
        success &= VERIFY(*rest == NULL);
    }

    /* Verify that we're well-behaved if argc is too big. */
    {
        dropt_char* args[] = { T("-n"), T("--hiddenFlag"), NULL };
        normalFlag = false;
        hiddenFlag = false;
        rest = dropt_parse(context, 100, args);
        success &= VERIFY(dropt_get_error(context) == dropt_error_none);
        success &= VERIFY(normalFlag == true);
        success &= VERIFY(hiddenFlag == true);
        success &= VERIFY(*rest == NULL);
    }

    /* Test that boolean options can be turned on with "=1" also. */
    {
        dropt_char* args[] = { T("-n=1"), T("--hiddenFlag=1"), NULL };
        normalFlag = false;
        hiddenFlag = false;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(normalFlag == true);
        success &= VERIFY(hiddenFlag == true);
        success &= VERIFY(*rest == NULL);
    }

    /* Test that boolean options can be turned off with "=0". */
    {
        dropt_char* args[] = { T("-n=0"), T("--hiddenFlag=0"), NULL };
        normalFlag = true;
        hiddenFlag = true;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(normalFlag == false);
        success &= VERIFY(hiddenFlag == false);
        success &= VERIFY(*rest == NULL);
    }

    /* Test that the last option wins if the same option is used multiple times. */
    {
        dropt_char* args[] = { T("-n=1"), T("-H"), T("-n=0"), T("--hiddenFlag=0"), NULL };
        normalFlag = false;
        hiddenFlag = false;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(normalFlag == false);
        success &= VERIFY(hiddenFlag == false);
        success &= VERIFY(*rest == NULL);
    }

    /* Test that normal boolean options don't consume the next argument. */
    {
        dropt_char* args[] = { T("-n"), T("1"), NULL };
        normalFlag = false;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(normalFlag == true);
        success &= VERIFY(rest == &args[1]);
    }

    {
        dropt_char* args[] = { T("--normalFlag"), T("1"), NULL };
        normalFlag = false;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(normalFlag == true);
        success &= VERIFY(rest == &args[1]);
    }

    /* Test grouping short boolean options. */
    {
        dropt_char* args[] = { T("-Hn"), NULL };
        hiddenFlag = false;
        normalFlag = false;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(hiddenFlag == true);
        success &= VERIFY(normalFlag == true);
        success &= VERIFY(*rest == NULL);
    }

    /* Test grouping short boolean options with a value. */
    {
        dropt_char* args[] = { T("-Hn=0"), NULL };
        hiddenFlag = false;
        normalFlag = true;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(hiddenFlag == true);
        success &= VERIFY(normalFlag == false);
        success &= VERIFY(*rest == NULL);
    }

    /* Test optional arguments with no acceptable argument provided. */
    {
        dropt_char* args[] = { T("-o"), T("-n"), NULL };
        optionalUInt.is_set = false;
        optionalUInt.value = 10;
        normalFlag = false;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(optionalUInt.is_set == true);
        success &= VERIFY(optionalUInt.value == 10);
        success &= VERIFY(normalFlag == true);
        success &= VERIFY(*rest == NULL);
    }

    {
        dropt_char* args[] = { T("--optionalUInt"), T("-n"), NULL };
        optionalUInt.is_set = false;
        optionalUInt.value = 10;
        normalFlag = false;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(optionalUInt.is_set == true);
        success &= VERIFY(optionalUInt.value == 10);
        success &= VERIFY(normalFlag == true);
        success &= VERIFY(*rest == NULL);
    }

    /* Test that optional arguments are consumed when possible. */
    {
        dropt_char* args[] = { T("-o"), T("42"), T("-n"), NULL };
        optionalUInt.is_set = false;
        optionalUInt.value = 10;
        normalFlag = false;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(optionalUInt.is_set == true);
        success &= VERIFY(optionalUInt.value == 42);
        success &= VERIFY(normalFlag == true);
        success &= VERIFY(*rest == NULL);
    }

    {
        dropt_char* args[] = { T("--optionalUInt"), T("42"), T("-n"), NULL };
        optionalUInt.is_set = false;
        optionalUInt.value = 10;
        normalFlag = false;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(optionalUInt.is_set == true);
        success &= VERIFY(optionalUInt.value == 42);
        success &= VERIFY(normalFlag == true);
        success &= VERIFY(*rest == NULL);
    }

    /* Test grouping short boolean options where one has an optional argument. */
    {
        dropt_char* args[] = { T("-on"), NULL };
        optionalUInt.is_set = false;
        optionalUInt.value = 10;
        normalFlag = false;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(optionalUInt.is_set == true);
        success &= VERIFY(optionalUInt.value == 10);
        success &= VERIFY(normalFlag == true);
        success &= VERIFY(*rest == NULL);
    }

    {
        dropt_char* args[] = { T("-no"), T("42"), NULL };
        normalFlag = false;
        optionalUInt.is_set = false;
        optionalUInt.value = 10;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(normalFlag == true);
        success &= VERIFY(optionalUInt.is_set == true);
        success &= VERIFY(optionalUInt.value == 42);
        success &= VERIFY(*rest == NULL);
    }

    /* Test options that require arguments. */
    {
        dropt_char* args[] = { T("-s"), NULL };
        stringVal = NULL;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(dropt_get_error(context) == dropt_error_insufficient_arguments);
        success &= VERIFY(*rest == NULL);
        dropt_clear_error(context);
    }

    {
        dropt_char* args[] = { T("--string"), NULL };
        stringVal = NULL;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(dropt_get_error(context) == dropt_error_insufficient_arguments);
        success &= VERIFY(*rest == NULL);
        dropt_clear_error(context);
    }

    {
        dropt_char* args[] = { T("--int"), T("42"), NULL };
        intVal = 0;
        rest = dropt_parse(context, 1, args);
        success &= VERIFY(dropt_get_error(context) == dropt_error_insufficient_arguments);
        success &= VERIFY(intVal == 0);
        success &= VERIFY(rest == &args[1]);
        dropt_clear_error(context);
    }

    /* Test options that require arguments with handlers that can accept NULL. */
    {
        dropt_char* args[] = { T("-r"), NULL };
        requiredArgFlag = false;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(dropt_get_error(context) == dropt_error_insufficient_arguments);
        success &= VERIFY(*rest == NULL);
        dropt_clear_error(context);
    }

    {
        dropt_char* args[] = { T("--requiredArgFlag"), NULL };
        requiredArgFlag = false;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(dropt_get_error(context) == dropt_error_insufficient_arguments);
        success &= VERIFY(*rest == NULL);
        dropt_clear_error(context);
    }

    /* Test grouping short options where one has a required argument. */
    {
        dropt_char* args[] = { T("-sn"), NULL };
        normalFlag = false;
        stringVal = NULL;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(dropt_get_error(context) == dropt_error_insufficient_arguments);
        success &= VERIFY(normalFlag == false);
        success &= VERIFY(*rest == NULL);
        dropt_clear_error(context);
    }

    {
        dropt_char* args[] = { T("-ns"), NULL };
        normalFlag = false;
        stringVal = NULL;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(dropt_get_error(context) == dropt_error_insufficient_arguments);
        success &= VERIFY(normalFlag == true);
        success &= VERIFY(*rest == NULL);
        dropt_clear_error(context);
    }

    {
        dropt_char* args[] = { T("-ns=foo"), NULL };
        normalFlag = false;
        stringVal = NULL;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(normalFlag == true);
        success &= VERIFY(string_equal(stringVal, T("foo")));
        success &= VERIFY(*rest == NULL);
        dropt_clear_error(context);
    }

    {
        dropt_char* args[] = { T("-ns"), T("foo"), NULL };
        normalFlag = false;
        stringVal = NULL;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(normalFlag == true);
        success &= VERIFY(string_equal(stringVal, T("foo")));
        success &= VERIFY(*rest == NULL);
        dropt_clear_error(context);
    }

    /* Test passing empty strings as arguments. */
    {
        dropt_char* args[] = { T("-s="), T("--string2="), NULL };
        stringVal = NULL;
        stringVal2 = NULL;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(string_equal(stringVal, T("")));
        success &= VERIFY(string_equal(stringVal2, T("")));
        success &= VERIFY(*rest == NULL);
    }

    {
        dropt_char* args[] = { T("-s"), T(""), T("--string2"), T(""), NULL };
        stringVal = NULL;
        stringVal2 = NULL;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(string_equal(stringVal, T("")));
        success &= VERIFY(string_equal(stringVal2, T("")));
        success &= VERIFY(*rest == NULL);
    }

    /* Test passing normal arguments. */
    {
        dropt_char* args[] = { T("-s=foo bar"), T("--string2=baz qux"), NULL };
        stringVal = NULL;
        stringVal2 = NULL;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(string_equal(stringVal, T("foo bar")));
        success &= VERIFY(string_equal(stringVal2, T("baz qux")));
        success &= VERIFY(*rest == NULL);
    }

    {
        dropt_char* args[] = { T("-s"), T("foo bar"), T("--string2"), T("baz qux"), NULL };
        stringVal = NULL;
        stringVal2 = NULL;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(string_equal(stringVal, T("foo bar")));
        success &= VERIFY(string_equal(stringVal2, T("baz qux")));
        success &= VERIFY(*rest == NULL);
    }

    /* Test arguments with embedded '=' characters. */
    {
        dropt_char* args[] = { T("-s=foo=bar"), T("--string2=baz=qux"), NULL };
        stringVal = NULL;
        stringVal2 = NULL;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(string_equal(stringVal, T("foo=bar")));
        success &= VERIFY(string_equal(stringVal2, T("baz=qux")));
        success &= VERIFY(*rest == NULL);
    }

    {
        dropt_char* args[] = { T("-s==foo"), T("--string2==bar"), NULL };
        stringVal = NULL;
        stringVal2 = NULL;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(string_equal(stringVal, T("=foo")));
        success &= VERIFY(string_equal(stringVal2, T("=bar")));
        success &= VERIFY(*rest == NULL);
    }

    /* Test that options that require arguments greedily consume the next
     * token, even if it looks like an option.
     */
    {
        dropt_char* args[] = { T("-s"), T("-n"), T("--string2"), T("-H"), NULL };
        stringVal = NULL;
        normalFlag = false;
        stringVal2 = NULL;
        hiddenFlag = false;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(string_equal(stringVal, T("-n")));
        success &= VERIFY(normalFlag == false);
        success &= VERIFY(string_equal(stringVal2, T("-H")));
        success &= VERIFY(hiddenFlag == false);
        success &= VERIFY(*rest == NULL);
    }

    /* Test dropt_attr_halt. */
    {
        dropt_char* args[] = { T("-h"), T("-n"), T("-h=invalid"), NULL };
        showHelp = false;
        normalFlag = false;
        hiddenFlag = false;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(showHelp == true);
        success &= VERIFY(normalFlag == false);
        success &= VERIFY(hiddenFlag == false);
        success &= VERIFY(rest == &args[1]);
    }

    /* Test --. */
    {
        dropt_char* args[] = { T("-n"), T("--"), T("-h"), NULL };
        normalFlag = false;
        hiddenFlag = false;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(normalFlag == true);
        success &= VERIFY(hiddenFlag == false);
        success &= VERIFY(rest == &args[2]);
    }

    /* Test -. */
    {
        dropt_char* args[] = { T("-n"), T("-"), T("-h"), NULL };
        normalFlag = false;
        hiddenFlag = false;
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
        success &= VERIFY(normalFlag == true);
        success &= VERIFY(hiddenFlag == false);
        success &= VERIFY(rest == &args[1]);
    }

    /* Test invalid options. */
    {
        dropt_char* args[] = { T("-X"), NULL };
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(dropt_get_error(context) == dropt_error_invalid_option);
        success &= VERIFY(*rest == NULL);
        dropt_clear_error(context);
    }

    {
        dropt_char* args[] = { T("-nX"), NULL };
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(dropt_get_error(context) == dropt_error_invalid_option);
        success &= VERIFY(*rest == NULL);
        dropt_clear_error(context);
    }

    {
        dropt_char* args[] = { T("-Xn"), NULL };
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(dropt_get_error(context) == dropt_error_invalid_option);
        success &= VERIFY(*rest == NULL);
        dropt_clear_error(context);
    }

    {
        dropt_char* args[] = { T("--bogus"), NULL };
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(dropt_get_error(context) == dropt_error_invalid_option);
        success &= VERIFY(*rest == NULL);
        dropt_clear_error(context);
    }

    {
        dropt_char* args[] = { T("--n"), NULL };
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(dropt_get_error(context) == dropt_error_invalid_option);
        success &= VERIFY(*rest == NULL);
        dropt_clear_error(context);
    }

    {
        dropt_char* args[] = { T("--normalFlagX"), NULL };
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(dropt_get_error(context) == dropt_error_invalid_option);
        success &= VERIFY(*rest == NULL);
        dropt_clear_error(context);
    }

    /* Test concatenated arguments. */
    {
        dropt_allow_concatenated_arguments(context, 1);

        {
            dropt_char* args[] = { T("-sfoo"), NULL };
            stringVal = NULL;
            rest = dropt_parse(context, -1, args);
            success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
            success &= VERIFY(string_equal(stringVal, T("foo")));
            success &= VERIFY(*rest == NULL);
        }

        dropt_allow_concatenated_arguments(context, 0);
    }

    /* Test some pathological cases. */
    {
        dropt_char* args[] = { T("-="), NULL };
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(dropt_get_error(context) == dropt_error_invalid_option);
        success &= VERIFY(*rest == NULL);
        dropt_clear_error(context);
    }

    {
        dropt_char* args[] = { T("--="), NULL };
        rest = dropt_parse(context, -1, args);
        success &= VERIFY(dropt_get_error(context) == dropt_error_invalid_option);
        success &= VERIFY(*rest == NULL);
        dropt_clear_error(context);
    }

    /* Test strncmp callback. */
    {
        {
            dropt_char* args[] = { T("-N"), NULL };
            rest = dropt_parse(context, -1, args);
            success &= VERIFY(dropt_get_error(context) == dropt_error_invalid_option);
            success &= VERIFY(*rest == NULL);
            dropt_clear_error(context);
        }

        {
            dropt_char* args[] = { T("--NORMALFLAG"), NULL };
            rest = dropt_parse(context, -1, args);
            success &= VERIFY(dropt_get_error(context) == dropt_error_invalid_option);
            success &= VERIFY(*rest == NULL);
            dropt_clear_error(context);
        }

        dropt_set_strncmp(context, dropt_strnicmp);

        {
            dropt_char* args[] = { T("-N"), NULL };
            normalFlag = false;
            rest = dropt_parse(context, -1, args);
            success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
            success &= VERIFY(normalFlag == true);
            success &= VERIFY(*rest == NULL);
        }

        {
            dropt_char* args[] = { T("--NORMALFLAG"), NULL };
            normalFlag = false;
            rest = dropt_parse(context, -1, args);
            success &= VERIFY(get_and_print_dropt_error(context) == dropt_error_none);
            success &= VERIFY(normalFlag == true);
            success &= VERIFY(*rest == NULL);
        }

        dropt_set_strncmp(context, NULL);
    }

    /* TO DO: Test repeated invocations of dropt_parse. */

    return success;
}


#ifdef DROPT_USE_WCHAR
int
wmain(int argc, wchar_t** argv)
#else
int
main(int argc, char** argv)
#endif
{
    dropt_char** rest;
    dropt_context* droptContext = NULL;

    bool success = test_strings();
    if (!success) { goto exit; }

    droptContext = dropt_new_context(options);
    if (droptContext == NULL)
    {
        fputts(T("Insufficient memory.\n"), stderr);
        success = false;
        goto exit;
    }

    success = test_dropt_handlers(droptContext);
    if (!success) { goto exit; }

    dropt_set_error_handler(droptContext, my_dropt_error_handler, NULL);

    init_option_defaults();
    success = test_dropt_parse(droptContext);
    if (!success) { goto exit; }

    init_option_defaults();
    dropt_allow_concatenated_arguments(droptContext, allowConcatenatedArgs);
    rest = dropt_parse(droptContext, -1, &argv[1]);

    /* Most programs normally should abort if given invalid arguments, but
     * for diagnostic purposes, this test program presses on anyway.
     */
    if (get_and_print_dropt_error(droptContext) != dropt_error_none) { fputtc(T('\n'), stdout); }

    if (showHelp)
    {
        fputts(T("Usage: test_dropt [options] [--] [operands]\n\n"), stdout);
#ifndef DROPT_NO_STRING_BUFFERS
        {
            dropt_help_params helpParams;
            dropt_init_help_params(&helpParams);
            helpParams.description_start_column = 30;
            helpParams.blank_lines_between_options = false;
            dropt_print_help(stdout, droptContext, &helpParams);
        }
#endif
        goto exit;
    }

    if (argc > 1 && !quiet)
    {
        dropt_char** arg;

        ftprintf(stdout, T("Compilation flags: %s%s%s\n")
                         T("normalFlag: %u\n")
                         T("requiredArgFlag: %u\n")
                         T("hiddenFlag: %u\n")
                         T("string: %s\n")
                         T("intVal: %d\n")
                         T("optionalUInt: %u, value: %u\n")
                         T("ipAddress: %u.%u.%u.%u (%u)\n")
                         T("\n"),
#ifdef NDEBUG
                 T("NDEBUG "),
#else
                 T(""),
#endif
#ifdef DROPT_NO_STRING_BUFFERS
                 T("DROPT_NO_STRING_BUFFERS "),
#else
                 T(""),
#endif
#ifdef DROPT_USE_WCHAR
                 T("DROPT_USE_WCHAR "),
#else
                 T(""),
#endif
                 normalFlag, requiredArgFlag, hiddenFlag,
                 (stringVal == NULL) ? T("(null)") : stringVal,
                 intVal, optionalUInt.is_set, optionalUInt.value,
                 (ipAddress >> 24) & 0xFF,
                 (ipAddress >> 16) & 0xFF,
                 (ipAddress >> 8) & 0xFF,
                 ipAddress & 0xFF,
                 ipAddress);
        ftprintf(stdout, T("Rest:"));
        for (arg = rest; *arg != NULL; arg++)
        {
            ftprintf(stdout, T(" %s"), *arg);
        }
        fputtc(T('\n'), stdout);
    }

exit:
    dropt_free_context(droptContext);

    if (!success) { fputts(T("One or more tests failed.\n"), stderr); }
    return success ? EXIT_SUCCESS : EXIT_FAILURE;
}

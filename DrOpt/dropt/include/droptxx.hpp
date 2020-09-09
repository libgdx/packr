/** droptxx.hpp
  *
  * A C++ wrapper for dropt.
  *
  * Copyright (c) 2008-2010 James D. Lin <jameslin@cal.berkeley.edu>
  *
  * The latest version of this file can be downloaded from:
  * <http://www.taenarum.com/software/dropt/>
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
  *
  * 2. Altered source versions must be plainly marked as such, and must not be
  *    misrepresented as being the original software.
  *
  * 3. This notice may not be removed or altered from any source distribution.
  */

#ifndef DROPT_HPP
#define DROPT_HPP

#include <string>
#include <iostream>
#include "dropt.h"


namespace dropt
{


typedef std::basic_string<dropt_char> string;
typedef std::basic_ostream<dropt_char> ostream;


#ifndef DROPT_NO_STRING_BUFFERS
class help_params
: public dropt_help_params
{
public:
    inline help_params()
    {
        dropt_init_help_params(this);
    }

    inline help_params& set_indent(unsigned int numSpaces)
    {
        indent = numSpaces;
        return *this;
    }

    inline help_params& set_description_start_column(unsigned int col)
    {
        description_start_column = col;
        return *this;
    }

    inline help_params& set_blank_lines_between_options(bool enable)
    {
        blank_lines_between_options = enable;
        return *this;
    }
};
#endif


/** dropt::context_ref is a simple C++ wrapper around dropt_context
  * functions.  It does not do any management of a dropt_context.
  */
class context_ref
{
public:
    context_ref(dropt_context* context);

    dropt_context* raw();

    const dropt_option* get_options() const;

    void set_error_handler(dropt_error_handler_func handler, void* handlerData);
    void set_strncmp(dropt_strncmp_func cmp);

    // Use this only for backward compatibility purposes.
    void allow_concatenated_arguments(bool allow = true);

    dropt_char** parse(int argc, dropt_char** argv);
    dropt_char** parse(dropt_char** argv);

    dropt_error get_error() const;
    void get_error_details(dropt_char** optionName, dropt_char** optionArgument) const;
    const dropt_char* get_error_message();
    void clear_error();

#ifndef DROPT_NO_STRING_BUFFERS
    string get_help(const help_params& helpParams = help_params()) const;
#endif

protected:
    dropt_context* mContext;
};


/** dropt::context is equivalent to dropt::context_ref but uses an
  * internally managed dropt_context instance.
  */
class context
: public context_ref
{
public:
    explicit context(const dropt_option* options);
    ~context();

private:
    // Intentionally unimplemented to be non-copyable.
    context(const context&);
    context& operator=(const context&);
};


dropt_error convert_exception();

// These use C++ bool and std::basic_string types.
dropt_option_handler_decl handle_bool;
dropt_option_handler_decl handle_verbose_bool;
dropt_option_handler_decl handle_string;

dropt_option_handler_decl handle_int;
dropt_option_handler_decl handle_uint;
dropt_option_handler_decl handle_double;


} // namespace dropt


#endif // DROPT_HPP

/** droptxx_example.cpp
  *
  * A simple dropt example.
  *
  * Written by James D. Lin and assigned to the public domain.
  *
  * The latest version of this file can be downloaded from:
  * <http://www.taenarum.com/software/dropt/>
  */

#include <iostream>
#include <vector>
#include <cstdlib>
#include <cstring>
#include <cassert>

#include "droptxx.hpp"


typedef enum { unknown, heads, tails } face_type;

/* Function prototype for our custom function to parse a string to a face_type. */
static dropt_option_handler_decl handle_face;


int
main(int argc, char** argv)
{
    bool showHelp = 0;
    bool showVersion = 0;
    int i = 0;
    face_type face = unknown;

    /* Each option is defined by a row in a table, containing properties
     * such as the option's short name (e.g. -h), its long name (e.g.
     * --help), its help text, its handler callback, and its callback data
     * (for typical handlers, this data is usually the address of a variable
     * for the handler to modify).
     *
     * See the dropt_option documentation in dropt.h for a complete list
     * of option properties.
     *
     * The table alternatively can be formed by adding options explicitly
     * as shown below:
     */
    dropt_option emptyOpt = { };
    dropt_option opt;
    std::vector<dropt_option> options;

    opt = emptyOpt;
    opt.short_name = 'h';
    opt.long_name = "help";
    opt.handler = dropt::handle_bool;
    opt.handler_data = &showHelp;
    opt.attr = dropt_attr_halt;
    options.push_back(opt);

    opt.short_name = '?';
    opt.long_name = NULL;
    opt.attr |= dropt_attr_hidden;
    options.push_back(opt);

    opt = emptyOpt;
    opt.long_name = "version";
    opt.description = "Shows version information.";
    opt.handler = dropt::handle_bool;
    opt.handler_data = &showVersion;
    opt.attr = dropt_attr_halt;
    options.push_back(opt);

    opt = emptyOpt;
    opt.short_name = 'i';
    opt.long_name = "int";
    opt.description = "Sample integer option.";
    opt.arg_description = "value";
    opt.handler = dropt::handle_int;
    opt.handler_data = &i;
    options.push_back(opt);

    opt = emptyOpt;
    opt.short_name = 'f';
    opt.long_name = "face";
    opt.description = "Sample custom option.";
    opt.arg_description = "{heads, tails}";
    opt.handler = handle_face;
    opt.handler_data = &face;
    options.push_back(opt);

    // Required sentinel value.
    options.push_back(emptyOpt);

    dropt::context droptContext(&options[0]);

    if (argc == 0)
    {
        /* This check is useless but is here for pedantic completeness.
         * Hosted C environments are not required to supply command-line
         * arguments, although obviously any environment that doesn't
         * supply arguments wouldn't have any use for dropt.
         */
    }
    else
    {
        /* Parse the arguments from argv.
         *
         * argv[1] is always safe to access since argv[argc] is guaranteed
         * to be NULL and since we've established that argc > 0.
         */
        char** rest = droptContext.parse(-1, &argv[1]);
        if (droptContext.get_error() != dropt_error_none)
        {
            std::cerr << "droptxx_example: " << droptContext.get_error_message() << std::endl;
            return EXIT_FAILURE;
        }
        else if (showHelp)
        {
            std::cout << "Usage: droptxx_example [options] [--] [operands]\n\n"
                      << "Options:\n"
                      << droptContext.get_help()
                      << std::endl;
        }
        else if (showVersion)
        {
            std::cout << "droptxx_example 1.0" << std::endl;
        }
        else
        {
            std::cout << "int value: " << i << "\n"
                      << "face value: " << face << "\n"
                      << "Operands: ";
            while (*rest != NULL)
            {
                std::cout << *rest << " ";
                rest++;
            }
            std::cout << std::endl;
        }
    }

    return EXIT_SUCCESS;
}


/** handle_face
  *
  *     An example of a custom option handler.  Usually the stock callbacks
  *     (e.g. dropt_handle_bool, dropt_handle_int, dropt_handle_string,
  *     etc.) should be sufficient for most purposes.
  */
static dropt_error
handle_face(dropt_context* context, const char* optionArgument, void* handlerData)
{
    dropt_error err = dropt_error_none;
    face_type* face = static_cast<face_type*>(handlerData);
    assert(face != NULL);

    /* Option handlers should handle 'optionArgument' being NULL (if the
     * option's argument is optional and wasn't supplied) or being the
     * empty string (if a user explicitly passed an empty string (e.g.
     * --face="").
     */
    if (optionArgument == NULL || optionArgument[0] == '\0')
    {
        err = dropt_error_insufficient_arguments;
    }
    else if (strcmp(optionArgument, "heads") == 0)
    {
        *face = heads;
    }
    else if (strcmp(optionArgument, "tails") == 0)
    {
        *face = tails;
    }
    else
    {
        // Reject the value as being inappropriate for this handler.
        err = dropt_error_mismatch;
    }

    return err;
}

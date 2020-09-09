/** dropt_example.c
  *
  * A simple dropt example.
  *
  * Written by James D. Lin and assigned to the public domain.
  *
  * The latest version of this file can be downloaded from:
  * <http://www.taenarum.com/software/dropt/>
  */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>

#include "dropt.h"


typedef enum { unknown, heads, tails } face_type;

/* Function prototype for our custom function to parse a string to a face_type. */
static dropt_option_handler_decl handle_face;


int
main(int argc, char** argv)
{
    /* C89 requires that array initializers be constant, so they need to
     * have static storage duration if their addresses are to be used as
     * initialization values.  This restriction is relaxed in C99 (most
     * compilers ignore it anyway).
     */
    static dropt_bool showHelp = 0;
    static dropt_bool showVersion = 0;
    static int i = 0;
    static face_type face = unknown;

    int exitCode = EXIT_SUCCESS;

    /* Each option is defined by a row in a table, containing properties
     * such as the option's short name (e.g. -h), its long name (e.g.
     * --help), its help text, its handler callback, and its callback data
     * (for typical handlers, this data is usually the address of a variable
     * for the handler to modify).
     *
     * See the dropt_option documentation in dropt.h for a complete list
     * of option properties.
     */
    dropt_option options[] = {
        { 'h',  "help", "Shows help.", NULL, dropt_handle_bool, &showHelp, dropt_attr_halt },
        { '?', NULL, NULL, NULL, dropt_handle_bool, &showHelp, dropt_attr_halt | dropt_attr_hidden },
        { '\0', "version", "Shows version information.", NULL, dropt_handle_bool, &showVersion, dropt_attr_halt },
        { 'i',  "int", "Sample integer option.", "value", dropt_handle_int, &i },
        { 'f',  "face", "Sample custom option.", "{heads, tails}", handle_face, &face },
        { 0 } /* Required sentinel value. */
    };

    dropt_context* droptContext = dropt_new_context(options);
    if (droptContext == NULL)
    {
        /* We failed to create the dropt context, possibly due to memory
         * allocation failure.
         *
         * This also can happen due to logical errors (e.g. if the options
         * array is malformed).  Logical errors will trigger DROPT_MISUSE()
         * and will terminate the program in debug builds.
         */
        exitCode = EXIT_FAILURE;
    }
    else if (argc == 0)
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
        char** rest = dropt_parse(droptContext, -1, &argv[1]);
        if (dropt_get_error(droptContext) != dropt_error_none)
        {
            fprintf(stderr, "dropt_example: %s\n", dropt_get_error_message(droptContext));
            exitCode = EXIT_FAILURE;
        }
        else if (showHelp)
        {
            printf("Usage: dropt_example [options] [--] [operands]\n\n"
                   "Options:\n");
            dropt_print_help(stdout, droptContext, NULL);
        }
        else if (showVersion)
        {
            printf("dropt_example 1.0\n");
        }
        else
        {
            printf("int value: %d\n", i);
            printf("face value: %d\n", face);

            printf("Operands: ");
            while (*rest != NULL)
            {
                printf("%s ", *rest);
                rest++;
            }
            printf("\n");
        }
    }

    dropt_free_context(droptContext);

    return exitCode;
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
    face_type* face = handlerData;
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
        /* Reject the value as being inappropriate for this handler. */
        err = dropt_error_mismatch;
    }

    return err;
}

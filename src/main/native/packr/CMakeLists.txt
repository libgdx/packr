#
# project: packr-native
#
cmake_minimum_required(VERSION 2.8)

# include the fips main cmake file
get_filename_component(FIPS_ROOT_DIR "../fips" ABSOLUTE)
include("${FIPS_ROOT_DIR}/cmake/fips.cmake")

set(CMAKE_OSX_DEPLOYMENT_TARGET "10.7")

fips_setup()
fips_project(packr-native)
fips_add_subdirectory(src)
fips_finish()

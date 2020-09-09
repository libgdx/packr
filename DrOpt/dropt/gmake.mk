# dropt common makefile for GNU make
#
# Written by James D. Lin and assigned to the public domain.
#
# The latest version of this file can be downloaded from:
# <http://www.taenarum.com/software/dropt/>

.SILENT:
.SUFFIXES:

ifndef SRC_ROOT
SRC_ROOT := $(CURDIR)
endif

ifndef BUILD_ROOT
BUILD_ROOT := $(SRC_ROOT)/build
endif

CFLAGS := -I$(SRC_ROOT)/include

OUT_DIR := $(BUILD_ROOT)/lib$(DEBUG_SUFFIX)$(NO_STRING_SUFFIX)$(UNICODE_SUFFIX)
OBJ_DIR := $(BUILD_ROOT)/tmp$(DEBUG_SUFFIX)$(NO_STRING_SUFFIX)$(UNICODE_SUFFIX)

GLOBAL_DEP := $(SRC_ROOT)/include/dropt.h $(SRC_ROOT)/include/dropt_string.h
GLOBALXX_DEP := $(GLOBAL_DEP) $(SRC_ROOT)/include/droptxx.hpp
LIB_OBJ_FILES := $(OBJ_DIR)/dropt.o $(OBJ_DIR)/dropt_handlers.o $(OBJ_DIR)/dropt_string.o
OBJ_FILES := $(LIB_OBJ_FILES) $(OBJ_DIR)/test_dropt.o
OBJXX_FILES := $(OBJ_DIR)/droptxx.o

DROPT_LIB := $(OUT_DIR)/libdropt.a
DROPTXX_LIB := $(OUT_DIR)/libdroptxx.a
EXAMPLE_EXE := $(OBJ_DIR)/dropt_example
EXAMPLEXX_EXE := $(OBJ_DIR)/droptxx_example
TEST_EXE := $(OBJ_DIR)/test_dropt


# Targets --------------------------------------------------------------

.PHONY: default all lib libxx
default: lib libxx
all: default example examplexx test
lib: $(DROPT_LIB)
libxx: $(DROPTXX_LIB)


.PHONY: example examplexx skip_example
ifdef DROPT_NO_STRING_BUFFERS
example examplexx: skip_example
else
ifdef _UNICODE
example examplexx: skip_example
else
example: $(EXAMPLE_EXE)
examplexx: $(EXAMPLEXX_EXE)
endif
endif

skip_example:
	@echo "(Skipping dropt_example and droptxx_example because either DROPT_NO_STRING_BUFFERS or _UNICODE was specified.)"


.PHONY: test
ifdef _UNICODE
test:
	@echo "(Skipping tests because _UNICODE was specified for gcc.)"
else
test: $(TEST_EXE)
	@echo "Running tests..."
	$(TEST_EXE) $(TEST_DROPT_ARGS)
	@echo "Tests passed."
endif


$(DROPT_LIB) $(DROPTXX_LIB): $(LIB_OBJ_FILES)
	-mkdir -p $(@D)
	$(AR) $(ARFLAGS) $@ $^

$(DROPTXX_LIB): $(OBJ_DIR)/droptxx.o

$(EXAMPLEXX_EXE): $(OBJ_DIR)/%: $(OBJ_DIR)/%.o $(DROPTXX_LIB)
	$(CXX) $(CFLAGS) $(CXXFLAGS) $< -L$(OUT_DIR) -ldroptxx -o $@

$(OBJ_DIR)/%: $(OBJ_DIR)/%.o $(DROPT_LIB)
	$(CC) $(CFLAGS) $< -L$(OUT_DIR) -ldropt -o $@

$(OBJ_FILES): $(OBJ_DIR)/%.o: $(SRC_ROOT)/src/%.c $(GLOBAL_DEP)
$(OBJ_DIR)/dropt_example.o: $(OBJ_DIR)/%.o: $(SRC_ROOT)/%.c $(GLOBAL_DEP)

$(OBJ_FILES) $(OBJ_DIR)/dropt_example.o:
	-mkdir -p $(@D)
	$(CC) -c -o $@ $(CFLAGS) $<
	@echo "$(<F)"

$(OBJXX_FILES): $(OBJ_DIR)/%.o: $(SRC_ROOT)/src/%.cpp $(GLOBALXX_DEP)
$(OBJ_DIR)/droptxx_example.o: $(OBJ_DIR)/%.o: $(SRC_ROOT)/%.cpp $(GLOBALXX_DEP)

$(OBJXX_FILES) $(OBJ_DIR)/droptxx_example.o:
	-mkdir -p $(@D)
	$(CXX) -c -o $@ $(CFLAGS) $(CXXFLAGS) $<
	@echo "$(<F)"


# Directories ----------------------------------------------------------

.PHONY: clean
clean:
	-rm -rf "$(BUILD_ROOT)"

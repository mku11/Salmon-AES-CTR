# salmon makefile

$(info Usage:)
$(info make PLATFORM=<value> ARCH=<value>)
$(info PLATFORM is the target OS, value: linux, macos)
$(info ARCH is the target architecture, value: x86_64, aarch64)
$(info DEBUG to add debug symbols, value: 0 to disable, 1 to enable)
$(info Example: make PLATFORM=linux ARCH=x86_64)
$(info )

PLATFORM?=win
ARCH?=x86_64
CC?=gcc

$(info PLATFORM: $(PLATFORM))
$(info ARCH: $(ARCH))
$(info )

SALMON_LIB_VERSION=3.0.2
SALMON_ROOT=../libs/salmon/salmon-gcc-$(PLATFORM)-$(ARCH)
SALMON_DIR=$(SALMON_ROOT)/salmon-gcc-$(PLATFORM)-$(ARCH).$(SALMON_LIB_VERSION)
SALMON_INCLUDE=$(SALMON_DIR)/salmon/include
SALMON_LIB=$(SALMON_DIR)/lib

ifeq ($(ARCH),aarch64)
ifeq ($(PLATFORM),linux)
  CC=aarch64-$(PLATFORM)-gnu-gcc
  CFLAGS+=-march=armv8-a+crypto
endif
else
  ARCH=x86_64
  CC=gcc
  CFLAGS+=-maes
endif

CFLAGS+=-I$(SALMON_INCLUDE)
CFLAGS+=-fPIC
LIBS=-lm -lsalmon
LIB_INCLUDE=-L$(SALMON_LIB)

ifeq ($(DEBUG),1)
CFLAGS+=-g -O0
else
CFLAGS+=-O2
endif

SRC=salmon_sample.c

OBJECT := $(SRC:.c=.o)

.PHONY: all
all: salmon_sample

%.o: %.c
	$(CC) -c -o $@ $< $(CFLAGS)

salmon_sample: $(OBJECT)
	cp -f $(SALMON_LIB)/* .
	$(CC) -o $@ $^ $(CFLAGS) $(LIB_INCLUDE) $(LIBS)
	
.PHONY: all

clean:
	rm -rf *.o *.so *.dll *.dylib salmon_sample
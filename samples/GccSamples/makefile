# salmon makefile

LIB_ROOT=./salmon-lib
SALMON_ROOT=$(LIB_ROOT)

SALMON_INCLUDE=$(SALMON_ROOT)/salmon/include

ifeq ($(ARCH), aarch64)
  CC=aarch64-linux-gnu-gcc
  CFLAGS+=-march=armv8-a+crypto
else
  ARCH=x86_64
  CC=gcc
  CFLAGS+=-maes
endif

CFLAGS+=-I$(SALMON_INCLUDE)
CFLAGS+=-fPIC
LIBS=-lm -lsalmon
LIB_INCLUDE=-L$(LIB_ROOT)/lib

SRC=salmon_sample.c

OBJECT := $(SRC:.c=.o)

.PHONY: all
all: salmon_sample

%.o: %.c
	$(CC) -c -o $@ $< $(CFLAGS)

salmon_sample: $(OBJECT)
	cp -f $(LIB_ROOT)/lib/* .
	$(CC) -o $@ $^ $(CFLAGS) $(LIB_INCLUDE) $(LIBS)
	
.PHONY: all

clean:
	rm -rf *.o *.so *.dll *.dylib salmon_sample
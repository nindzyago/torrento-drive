cmake_minimum_required(VERSION 3.22)
project(byedpi C)

# byedpi (https://github.com/hufrea/byedpi, MIT) is vendored unchanged in byedpi/; the commit is in byedpi/COMMIT.
# Its main() is renamed so the library has no entry point of its own: byedpi_jni.c starts it.
set(BYEDPI_SRC
    byedpi/packets.c byedpi/main.c byedpi/conev.c byedpi/proxy.c
    byedpi/desync.c byedpi/mpool.c byedpi/extend.c)

add_library(byedpi SHARED ${BYEDPI_SRC} byedpi_jni.c)
target_include_directories(byedpi PRIVATE byedpi)
target_compile_definitions(byedpi PRIVATE _DEFAULT_SOURCE main=byedpi_main)
target_compile_options(byedpi PRIVATE -std=gnu99 -O2 -Wno-unused -Wno-unused-parameter)
target_link_libraries(byedpi log)

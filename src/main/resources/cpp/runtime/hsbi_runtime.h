#ifndef HSBI_RUNTIME_H
#define HSBI_RUNTIME_H

#include <iostream>
#include <string>

using namespace std;

inline void print_int(int v)       { cout << v << endl; }
inline void print_bool(bool v)     { cout << (v ? 1 : 0) << endl; }
inline void print_char(char v)     { cout << v << endl; }
inline void print_string(string v) { cout << v << endl; }

#endif // HSBI_RUNTIME_H

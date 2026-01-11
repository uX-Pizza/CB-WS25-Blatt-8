## Ausführen der Testfälle mit dem g++
#
# Nutzung:
#   make                       -> führt alle Positiv- und Negativtests aus
#   make pos                   -> nur Positivtests
#   make neg                   -> nur Negativtests (müssen scheitern)
#   make list                  -> listet gefundene Tests
#
# Voraussetzungen:
#   - Tests liegen unter tests/pos/*.cpp und tests/neg/*.cpp
#   - Runtime liegt unter runtime/hsbi_runtime.h
#   - Positivtests enthalten am Dateiende einen Block:
#       /*
#        * EXPECT:
#        * <Zeilen der erwarteten Ausgabe>
#        */
#
# Hinweis:
#   CXX kann auf clang++ gesetzt werden (z. B. `make CXX=clang++`)

CXX      ?= g++
CXXFLAGS ?= -std=c++17 -O2 -Wall -Wextra -pedantic-errors -I runtime/

POS_SRCS  := $(wildcard tests/pos/*.cpp)
NEG_SRCS  := $(wildcard tests/neg/*.cpp)

POS_NAMES := $(notdir $(basename $(POS_SRCS)))
NEG_NAMES := $(notdir $(basename $(NEG_SRCS)))

BUILD_DIR := build
POS_BUILD := $(BUILD_DIR)/pos
NEG_BUILD := $(BUILD_DIR)/neg
EXP_DIR   := $(BUILD_DIR)/expected

.PHONY: all pos neg clean list help $(POS_NAMES) $(NEG_NAMES)

all: pos neg

pos: $(addprefix $(POS_BUILD)/, $(POS_NAMES:%=%._ok))

neg: $(addprefix $(NEG_BUILD)/, $(NEG_NAMES:%=%._ok))

$(POS_BUILD)/%._ok: tests/pos/%.cpp
	@mkdir -p $(POS_BUILD) $(EXP_DIR)
	@echo "==> POS $*"
	$(CXX) $(CXXFLAGS) $< -o $(POS_BUILD)/$*
	@awk '/\/\* *EXPECT/{flag=1; next} /\*\//{if(flag){exit}} flag{print}' $< > $(EXP_DIR)/$*.out
	@$(POS_BUILD)/$* > $(POS_BUILD)/$*.out
	@diff -u $(EXP_DIR)/$*.out $(POS_BUILD)/$*.out && echo "[OK] $*" || (echo "[FAIL] $*"; exit 1)
	@touch $@

$(NEG_BUILD)/%._ok: tests/neg/%.cpp
	@mkdir -p $(NEG_BUILD)
	@echo "==> NEG $* (Compilation sollte fehlschlagen)"
	@if $(CXX) $(CXXFLAGS) $< -o $(NEG_BUILD)/$* 2> $(NEG_BUILD)/$*.err; then \
		echo "[FAIL] $*: Compilation unerwartet erfolgreich"; exit 1; \
	else \
		echo "[OK] $*: Compilation wie erwartet fehlgeschlagen"; \
	fi
	@touch $@

list:
	@echo "Gefundene Positivtests:"; \
	for t in $(POS_NAMES); do echo "  $$t"; done; \
	echo ""; \
	echo "Gefundene Negativtests:"; \
	for t in $(NEG_NAMES); do echo "  $$t"; done

clean:
	rm -rf $(BUILD_DIR) a.out *.o *.~ core

help:
	@echo "Ziele:"
	@echo "  make          -> alle Tests (pos + neg)"
	@echo "  make pos      -> nur Positivtests"
	@echo "  make neg      -> nur Negativtests"
	@echo "  make list     -> Tests auflisten"

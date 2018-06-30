#!/bin/sh

PREFIX="${PREFIX:-$HOME/local}"
BIN_DIR="${PREFIX}/bin"
JRUN_PATH="${BIN_DIR}/jrun"    JRUN_URL="https://raw.githubusercontent.com/ctrueden/jrun/3ddde431f6387d76088a12f1eb3fd8f49ff4091d/jrun"

if [ ! -f "${JRUN_PATH}" ]; then
    curl "${JRUN_URL}" -o "${JRUN_PATH}"
fi

chmod u+x "${JRUN_PATH}"

cp paintera "${BIN_DIR}"

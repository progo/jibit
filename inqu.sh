#!/bin/sh
ROOTDIR=`dirname "$0"`
cd "$ROOTDIR"/inquirer/inqu
uv run ./src/inqu/main.py "$1"


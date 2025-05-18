#!/bin/sh
ROOTDIR=`dirname "$0"`
. "$ROOTDIR"/inquirer/.venv/bin/activate
python "$ROOTDIR"/inquirer/inqu/src/inqu/main.py "$1"


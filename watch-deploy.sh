#!/bin/sh
set -eux
ls deploy.sh target/scala-2.13/ps17-google-sheets-fastopt/main.js | entr -r -c sh ./deploy.sh

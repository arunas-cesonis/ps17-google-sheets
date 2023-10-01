#!/bin/sh
set -eux
_make () {
  JS=./target/scala-2.13/ps17-google-sheets-fastopt/main.js
  echo 'var SaxParser = (function () { var exports = {}; '
  cat ./node_modules/sax-parser/lib/sax-parser.js
  echo 'return exports.SaxParser; }()); '
  cat ${JS}
  grep -o 'deploy[A-Za-z_0-9]*' ${JS} | sort -u | awk '{
    print "function "substr($1, 8)"() { return "$1".apply(this, arguments); }";
  }'
  cat ./config.js
}
_make > clasp/main.js
cd clasp
clasp push
#clasp run getAllProducts

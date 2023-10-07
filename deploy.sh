#!/bin/sh
set -eux
_make () {
  JS=./dist/index.js
  cat ${JS}
  grep -o 'deploy[A-Za-z_0-9]*' ${JS} | sort -u | awk '{
    print "function "substr($1, 8)"() { return globalThis.main."$1".apply(this, arguments); }";
  }'
}
rm -rf ./.parcel-cache

# without one of the --no-* options, the script OOM's in App Script environment
# (somewhere inside Schema.from call)
./node_modules/.bin/parcel build --no-optimize --no-scope-hoist --no-cache src/main/js/index.js

_make > clasp/main.js
cd clasp
clasp push
#clasp run getAllProducts

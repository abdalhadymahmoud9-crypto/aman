# Attribution

## Blocklist source: StevenBlack/hosts

The built-in domain list is derived from the "porn" hosts file published
by the StevenBlack/hosts project:

  https://github.com/StevenBlack/hosts
  (file: alternates/porn/hosts, fetched 24 November 2026)

That project aggregates adult-content domain lists from reputable
sources. It is released under the MIT License, reproduced in full below
from https://github.com/StevenBlack/hosts/blob/master/license.txt

The scripts in `tools/` transform the fetched hosts file (parse, fold
covered subdomains, drop invalid DNS names) to produce the app's and
extension's generated blocklist files.

------------------------------------------------------------------------

The MIT License (MIT)

Copyright (c) 2026 Steven Black

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

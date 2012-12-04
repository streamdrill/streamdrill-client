#! /bin/sh
BASE="http://demo.streamdrill.com"
URL="/1/query/$1"
KEY="f9aaf865-b89a-444d-9070-38ec6666e539"
SEC="9e13e4ac-ad93-4c8f-a896-d5a937b84c8a"
DATE=`LC_ALL=en_US TZ=UTC date +"%a, %d %b %Y %H:%M:%S GMT"`
SIG=`echo -n -e "GET\n$DATE\n$URL" | openssl dgst -sha1 -hmac $SEC -binary | base64`
curl -s -H "Date: $DATE" -H "Authorization: TPK $KEY:$SIG" "$BASE$URL"

#! /bin/bash
if [ "$#" == "0" ]; then echo "usage: create [-b baseUri ] <trend> <entities>"; exit 0; fi
if [ "$1" == "-b" ]; then BASE=$2; shift 2; else
  BASE="http://demo.streamdrill.com"
fi
URL="/1/create/$1/$2"
KEY="f9aaf865-b89a-444d-9070-38ec6666e539"
SEC="9e13e4ac-ad93-4c8f-a896-d5a937b84c8a"
DATE=$(LC_ALL=en_US TZ=UTC date +"%a, %d %b %Y %H:%M:%S GMT")
SIG=$(echo -ne "GET\n$DATE\n$URL" | openssl dgst -sha1 -hmac $SEC -binary | base64)
curl -s -H "Date: $DATE" -H "Authorization: TPK $KEY:$SIG" "$BASE$URL?$3"

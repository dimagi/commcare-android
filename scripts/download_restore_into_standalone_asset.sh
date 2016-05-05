#!/bin/sh
# args: $1 = domain, $2 = username, $3 = password, $4 = path to asset dir
# download the given user's restore file into the standalone assets directory

DOMAIN=$1
USERNAME=$2
PASSWORD=$3
ASSET_DIR=$4

curl --basic -u $USERNAME@$DOMAIN.commcarehq.org:$PASSWORD "https://www.commcarehq.org/a/aliza-test/phone/restore/?version=2.0" -o "$ASSET_DIR/local_restore_payload.xml"

exit 0

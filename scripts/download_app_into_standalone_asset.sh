#!/bin/sh
# args: $1 = domain, $2 = app_id, $3 = path to asset dir, $4 = version_number
# download and extract commcare app with id of $1

DOMAIN=$1
APP_ID=$2
ASSET_DIR=$3/direct_install
VERSION_NUMBER=$4

echo "Downloading $1 (id $2) into $ASSET_DIR"

mkdir -p $ASSET_DIR

wget "https://www.commcarehq.org/a/$DOMAIN/apps/api/download_ccz?app_id=$APP_ID&version=$VERSION_NUMBER&include_multimedia=true" -O "$ASSET_DIR/ccapp.zip"

cd $ASSET_DIR
unzip -o ccapp.zip
rm ccapp.zip
exit 0

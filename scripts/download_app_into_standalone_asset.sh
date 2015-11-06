# download and extract commcare app with id of $1

ASSET_DIR=$3/direct_install
echo "Downloading $1 (id $2) into $ASSET_DIR"

mkdir -p $ASSET_DIR

wget "https://www.commcarehq.org/a/$1/apps/api/download_ccz/?app_id=${2}#hack=commcare.ccz" -O "$ASSET_DIR/ccapp.zip"

cd $ASSET_DIR
unzip -o ccapp.zip
rm ccapp.zip
exit 0

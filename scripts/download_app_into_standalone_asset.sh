# download and extract commcare app with id of $1

ASSET_DIR=$2/direct_install
echo "Downloading $1 into $ASSET_DIR"

mkdir -p $ASSET_DIR

wget "https://www.commcarehq.org/a/corpora/apps/api/download_ccz/?app_id=$1#hack=commcare.ccz" -O "$ASSET_DIR/ccapp.zip"

cd $ASSET_DIR
unzip -o ccapp.zip
rm ccapp.zip
exit 0

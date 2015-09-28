echo "downloading $1 into $2"
mkdir -p $2/direct_install
wget "https://www.commcarehq.org/a/corpora/apps/api/download_ccz/?app_id=$1#hack=commcare.ccz" -O "$2/direct_install/ccapp.zip"

cd $2/direct_install
unzip -o ccapp.zip
rm ccapp.zip
exit 0

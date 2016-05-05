
import subprocess 

#Apps are 5-tuples consisting of (app-id, app-domain, build-number, username, password)
APPS_LIST = [("a370e321169d2555a86d3e174f3024c2", "aliza-test", 53, "t1", "123")] #, ("73d5f08b9d55fe48602906a89672c214", "aliza-test", 49, "t1", "123")]

RELATIVE_PATH_TO_ASSETS_DIR = "./app/standalone/assets"


def download_ccz(app_id, domain, build_number):
	#TODO: Get HQ to implement downloading a specific build
	subprocess.call(["./scripts/download_app_into_standalone_asset.sh", domain, app_id, RELATIVE_PATH_TO_ASSETS_DIR]) 


def download_restore_file(domain, username, password):
	subprocess.call(["./scripts/download_restore_into_standalone_asset.sh", domain, username, password, RELATIVE_PATH_TO_ASSETS_DIR])


def assemble_apk():
	subprocess.call(["gradle", "assembleStandaloneDebug", "-PversionCode={}".format(build_number)])


def move_apk(app_id):
	subprocess.call(["mkdir", "-p", "./build/consumer_apks"]) 
	subprocess.call(["mv", "./build/outputs/apk/commcare-odk-standalone-debug.apk", "./build/outputs/consumer_apks/{}.apk".format(app_id)])


for (app_id, domain, build_number, username, password) in APPS_LIST:
	download_ccz(app_id, domain, build_number)
	download_restore_file(domain, username, password)
	assemble_apk()
	move_apk(app_id)


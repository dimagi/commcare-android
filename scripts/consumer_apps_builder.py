
import subprocess 

#Apps are 5-tuples consisting of (app-id, app-domain, build-number, username, password)
APPS_LIST = [("a370e321169d2555a86d3e174f3024c2", "aliza-test", 53)] #("b51238b80b8357ad60d45048e1305674", "mlabour", 209)

RELATIVE_PATH_TO_ASSETS_DIR = "../app/standalone/assets"


def download_ccz(app_id, domain, build_number):
	#TODO: Get HQ to implement downloading a specific build
	subprocess.call(["./download_app_into_standalone_asset.sh", domain, app_id, RELATIVE_PATH_TO_ASSETS_DIR]) 


def download_restore_file(domain, username, password):


for (app_id, domain, build_number, username, password) in APPS_LIST:
	download_ccz(app_id, domain, build_number)

	download_restore_file(domain, username, password)
	#subprocess.call("gradle assembleStandaloneDebug -PversionCode={}".format(build_number))
	#subprocess.call("mv build/outputs/apk/commcare.apk build/consumer_apks/{}".format(app_id))


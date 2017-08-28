from Crypto.PublicKey import RSA
from Crypto.Hash import SHA256
from Crypto.Signature import PKCS1_PSS

import sys

from cryptography import x509
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives.serialization import Encoding
from cryptography.hazmat.primitives.serialization import PublicFormat

import base64

with open("cert_for_tests.cer", "rb") as cert_file:
    certificate = x509.load_pem_x509_certificate(
            cert_file.read(),
            backend=default_backend()
    )

with open("key_for_tests.pem", "rb") as key_file:
    private_key = RSA.importKey(key_file.read())


def main(argv):
    if len(argv) ==0 :
        print "Provide a payload to sign"
    else:
        sign(argv[0])
    
    
def sign(message):
    cert_bytes = certificate.public_key().public_bytes(Encoding.DER, PublicFormat.SubjectPublicKeyInfo)

    encoded_cert = base64.b64encode(cert_bytes)


    print "CommCare Compatible Key String: %s" % encoded_cert

    sha256_hash = SHA256.new(message)
    signature = PKCS1_PSS.new(private_key).sign(sha256_hash)


    encoded_signature = base64.b64encode(signature)
    print "message: [%s]\nsignature: [%s]" % (message, encoded_signature)

if __name__ == "__main__":
    main(sys.argv[1:])
    

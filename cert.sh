# Copyright © 2021 Atomist, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

cat >server.conf <<EOF
[req]
prompt=no
distinguished_name = req_distinguished_name
req_extensions = v3_req

[req_distinguished_name]
commonName=policy-controller.atomist.svc
stateOrProvinceName = CA
countryName = US
emailAddress = jim@atomist.com
organizationName = Atomist
organizationalUnitName = Development

[ v3_req ]
basicConstraints = CA:FALSE
keyUsage = nonRepudiation, digitalSignature, keyEncipherment
extendedKeyUsage = clientAuth, serverAuth
subjectAltName = @alt_names

[ alt_names ]
DNS.0 = policy-controller.atomist.svc

EOF

openssl genrsa -out ca.key 2048
openssl req -x509 -new -nodes -key ca.key -days 100000 -out ca.crt -subj "/CN=admission_ca"
openssl genrsa -out server.key 2048
openssl req -new -nodes -key server.key -out server.csr -config server.conf
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.crt -days 100000 -extensions v3_req -extfile server.conf
openssl pkcs12 -inkey server.key -in server.crt -export -out server.pkcs12 -legacy -passout file:password.txt

# put base64 encoded ca.crt into admission controller webhook configuration
CA=$(cat ca.crt | base64 | /usr/bin/tr -d '\n')
echo '[{"op": "replace", "path": "/webhooks/0/clientConfig/caBundle", "value": '"\"$CA\""'}]' > resources/k8s/admission/ca.json

# put password and server.pkcs12 into admission controller secrets
cp server.pkcs12 resources/k8s/controller
echo "password=$(< password.txt)" > resources/k8s/controller/keystore.env

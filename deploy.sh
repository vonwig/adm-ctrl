###########################
# increment version.txt
# package into an OCI image
# deploy to ECR
# update k8s cluster
###########################
BASEDIR=$(dirname "$0")
export DOCKER_CONFIG=$BASEDIR/.docker

set -e

if [ -n "$(git status --porcelain)" ]; then
  echo "dirty jib. stop."
  exit -1
fi 

# increment version
bb -e '(spit "version.txt" (format "v%s" (inc (Integer. (str (second (re-find #"v(\d+)" (str/trim (slurp "version.txt")))))))))'
# build OCI image to dockerhub
clj -Tjib build

# Update K8 cluster
pushd resources/k8s/controller
kustomize edit set image atomist/adm-ctrl:$(cat ../../../version.txt)
kustomize build . | kubectl apply -f -
popd


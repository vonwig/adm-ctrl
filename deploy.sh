###########################
# increment version.txt
# package into an OCI image
# deploy to ECR
# update k8s cluster
###########################
BASEDIR=$(dirname "$0")
export DOCKER_CONFIG=$BASEDIR/.docker

set -e

# increment version
bb -e '(spit "version.txt" (format "v%s" (inc (Integer. (str (second (re-find #"v(\d+)" (str/trim (slurp "version.txt")))))))))'
# build OCI image to docker
clj -T:jib build :config "$(./update-jib-config.clj)"
# TODO get rid of this and use jib to push directly to DockerHub
docker push atomist/adm-ctrl:$(cat version.txt)

# Update K8 cluster
pushd resources/k8s/controller
kustomize edit set image atomist/adm-ctrl:$(cat ../../../version.txt)
kustomize build . | kubectl apply -f -
popd


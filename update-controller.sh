pushd resources/k8s/controller
kustomize edit set image atomist/adm-ctrl:$1
kustomize build ./ > full.yaml
k apply -f full.yaml
popd


clj -T:jib build :main atomist.adm-ctrl.handler :docker atomist/adm-ctrl:$1 :target-creds creds.edn
docker push atomist/adm-ctrl:$1

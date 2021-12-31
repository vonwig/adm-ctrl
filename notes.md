## Build

1.  verify that there's a local `.creds.edn` with atomistbot dockerhub creds
2.  run publish.sh - it will fail if the working dir is not clean

## Notes

* `scope: "Namespaced"` indicates that we should only match resources in a namespace (not cluster resources)
* `name: "policy-controller.atomist.com:` must be a valid DNS subdomain name.
* each `ValidatingWebhookConfiguration` can define several webhooks (each with a unique name).  We specify just one here.
* todo - add `namespace` selector
* `failurePolicy: Ignore` indicates that the api request should be allowed to continue when the request fails

# Running on EKS

How do you specify the `--admission-control-config-file` flag in EKS?  This could permit us to create an authenticated webhook push to an external cluster but not if EKS does not support setting up a new `AdmissionConfiguration` to configure how to authenticate to the remote endpoint.

[authenticating-admission-webhook]: https://kubernetes.io/docs/reference/access-authn-authz/extensible-admission-controllers/#authenticate-apiservers

## Image

The default plugins do not include the [`ImagePolicyWebhook`](https://kubernetes.io/docs/reference/access-authn-authz/admission-controllers/#imagepolicywebhook).  Default set of plugins are shown here.

```
CertificateApproval, CertificateSigning, CertificateSubjectRestriction, DefaultIngressClass, DefaultStorageClass, DefaultTolerationSeconds, LimitRanger, MutatingAdmissionWebhook, NamespaceLifecycle, PersistentVolumeClaimResize, Priority, ResourceQuota, RuntimeClass, ServiceAccount, StorageObjectInUseProtection, TaintNodesByCondition, ValidatingAdmissionWebhook
```

Turning on an `ImagePolicyWebhook` typically requires the the api-server be reconfigured.

## SSL

EKS in kube version `1.20` has issues with certain certificates:

```
x509: certificate relies on legacy Common Name field, use SANs or temporarily enable Common Name matching with GODEBUG=x509ignoreCN=0
```

[dynamic-admission-control]: https://kubernetes.io/docs/reference/access-authn-authz/extensible-admission-controllers/
[image-policy-webhook]: https://kubernetes.io/docs/reference/access-authn-authz/admission-controllers/#imagepolicywebhook
[deprecated-pod-security-policies]: https://www.antitree.com/2020/11/pod-security-policies-are-being-deprecated-in-kubernetes/


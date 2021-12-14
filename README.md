## Notes

* `scope: "Namespaced"` indicates that we should only match resources in a namespace (not cluster resources)
* `name: "policy-controller.atomist.com:` must be a valid DNS subdomain name.
* each `ValidatingWebhookConfiguration` can define several webhooks (each with a unique name).  We specify just one here.
* todo - add `namespace` selector
* `failurePolicy: Ignore` indicates that the api request should be allowed to continue when the request fails
*
*

How do you specify the `--admission-control-config-file` flag in EKS?  This could permit us to create an authenticated webhook push to an external cluster but not if EKS does not support setting up a new `AdmissionConfiguration` to configure how to authenticate to the remote endpoint.

[authenticating-admission-webhook]: https://kubernetes.io/docs/reference/access-authn-authz/extensible-admission-controllers/#authenticate-apiservers

## Image

The default plugins do not include the [`ImagePolicyWebhook`](https://kubernetes.io/docs/reference/access-authn-authz/admission-controllers/#imagepolicywebhook).  Default set of plugins are shown here.

```
CertificateApproval, CertificateSigning, CertificateSubjectRestriction, DefaultIngressClass, DefaultStorageClass, DefaultTolerationSeconds, LimitRanger, MutatingAdmissionWebhook, NamespaceLifecycle, PersistentVolumeClaimResize, Priority, ResourceQuota, RuntimeClass, ServiceAccount, StorageObjectInUseProtection, TaintNodesByCondition, ValidatingAdmissionWebhook
```

Turning on an `ImagePolicyWebhook` typically requires the the api-server be reconfigured.

[dynamic-admission-control]: https://kubernetes.io/docs/reference/access-authn-authz/extensible-admission-controllers/
[image-policy-webhook]: https://kubernetes.io/docs/reference/access-authn-authz/admission-controllers/#imagepolicywebhook
[]: 

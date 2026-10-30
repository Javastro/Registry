# Publishing content

Records that you publish are added through the admin endpoint, using HTTP Basic authentication as the `admin` user:

```shell
curl -u admin:betterpassword -X POST -H "Content-Type: text/xml" \
     --data-binary @resources.xml http://localhost:8080/admin/strictAdd
```

The body is a `ri:Resource` (or `ri:VOResources`) document. `strictAdd` validates the content before it is stored.
Stored records appear in the OAI-PMH `ListRecords`/`GetRecord` responses of the `ivo_managed` set.

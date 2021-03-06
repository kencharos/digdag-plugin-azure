# digdag-plugin-azure

digdag plugin for Microsoft Azure.

## version 

0.1.1

## Tasks

### blob_wait

operator waits for file to appear in Azure Blob Storage.

this task uses [list blobs](https://docs.microsoft.com/en-us/rest/api/storageservices/list-blobs), 
so that you can use path of prefix.

#### Secrets

+ azure.blob.connectionString - your storage account connectionString.

#### Options

+ blob_wait>: path or prefix from container (required)
+ container: container name (required)

#### Outputs

+ blob.last_object
    + name
    + metadata
    + properties


#### Examples

this example waits "logs/current/sample.log"

```
blob_wait>: current/sample.log
container: logs
```

this example waits any files in "upload/some/directory/"

```
blob_wait>: some/directory/
container: upload
```


### storage_queue_wait

operator waits for message to appear in Azure Queue Storage.
You can choose peek or retrieve message.  peek is default.


#### Secrets

+ azure.queue.connectionString - your storage account connectionString.

#### Options

+ storage_queue_wait>: queueName (required)
+ retrieve: if true, message get by retrieve. if false, message get by peek default false.
+ visibilityTimeout: if retrieve is true, use this value as visibilityTimeout in seconds. default is 30.


#### Outputs

+ queue.last_object
    + messageId
    + message
    + popReceipt
    + insertionTime
    + expirationTime
    + nextVisibleTime

#### Examples

wait message by peek

```
storage_queue_wait>: some-queue
```

wait message by retrieve with custom visibilityTimeout

```
storage_queue_wait>: some-queue
retrieve: true
visibilityTimeout: 300
```


## TODO

- [ ] unit tests

## Usage

### 1) Azure Setup

Setup your Azure Storage Account, make Blob Storage Container, get ConnectionString.
if you run local mode, make `~/.config/digdag/config` as follows:

```
secrets.azure.blob.connectionString=<Your Storage Account ConnectionString>
```

### 2) build

```sh
./gradlew publish
```

Artifacts are build on local repos: `./build/repo`.

### 3) run an example

Edit sample/sample.dig for container and blob name.

Then run

```sh
digdag selfupdate
digdag run --project sample sample.dig -p repos=`pwd`/build/repo

-p repos=C:\Users\kentaro.maeda.ULDOMAIN\Documents\git\digdag\digdag-plugin-azure\build\repo
```

blob_wait task will poll until blob created.

## Plugin Loading

Digdag loads pluigins from Maven repositories.

You can use a local Maven repository (local FS, Amazon S3) or any public Maven repository ([Maven Central](http://search.maven.org/), [Sonatype](https://www.sonatype.com/), [Bintary](https://bintray.com/), [Jitpack](https://jitpack.io/)) for the plugin artifact repository.

### Publishing your plugin using Github and Jitpack

[Jitpack](https://jitpack.io/) is useful for publishing your github repository as a maven repository.

```sh
git tag <version>
git push origin <version>
```

https://jitpack.io/<your account>/digdag-plugin-azure/<version>

Now, you can load the artifact from dig file as follows:

```
_export:
  plugin:
    repositories:
      - https://jitpack.io
    dependencies:
      - com.github.<your account>:digdag-plugin-azure:<version>
```

## Further reading

- [Operators](http://docs.digdag.io/operators.html) and [their implementations](https://github.com/treasure-data/digdag/tree/master/digdag-standards/src/main/java/io/digdag/standards/operator)

![Burst](../documentation/burst_h_small.png "")
--

![](./doc/catalog.png "")

___Catalog___ is the metadata management/storage subsystem for Burst. It combines
simple JDBC based persistence with a full featured Thrift API.  It is designed to
be simple and high performance. Most object types are contained in a single
table with one row per instance and accessed using a simple `long` autogenerated
primary key. Access patterns that rely on joins or non primary key lookups are
avoided in primary use cases.

* [Metadata](./doc/metadata.md)
* [Schema Evolution](./doc/evolution.md)

## Thrift API
The relevant Thrift types and service endpoints are
defined in [BurstCatalogApi.thrift](src/main/thrift/catalogService.thrift)

## Model
The Catalog implements a metadata types and basic semantics as defined
in the [Fabric](../burst-fabric/readme.md) module. These types are
used throughout the Burst ecosystem, however the Catalog is where
these data structures are accessible via external APIs and are persisted.
The Burst Supervisor hosts the Catalog server and the Catalog client is used internally
and made available externally.

#### Cross Cell
While this is not required, the Catalog data model is defined so that
it is a normal operational mode to have the
same JDBC server and database shared across multiple Catalog Cell instances.
All types are name spaced so that meta-data operations can be executed
that cross Cell boundaries. This supports both global exploration and discovery
of metadata, but also transactional movement between cells.


## Configuration
|  system property |  default |  description |
|---|---|---|
|  burst.catalog.name |  "catalog" |  user friendly name of application  |
|  burst.catalog.api.host |  "0.0.0.0" |  interface to bind Thrift API  |
|  burst.catalog.api.port |  37010 |  port to bind Thrift API  |
|  burst.catalog.api.timeout.ms |  120 seconds |  Thrift API timeout (in ms)  |
|  burst.catalog.db.host |  "localhost" |  Catalog sql database hostname  |
|  burst.catalog.db.name |  "burst_catalog" |  name of database  |
|  burst.catalog.db.user |  "burst" |  user of database  |
|  burst.catalog.db.password |  "burst" |  password of database  |
|  burst.catalog.db.port |  3306 |  port of database  |
|  burst.catalog.db.connections.max |  500 |  size of connection pool  |
|  burst.catalog.server.connect.life.ms |  infinite |  Thrift API server connection lifetime (in ms)  |
|  burst.catalog.server.connect.idle.ms |  infinite |   Thrift API server connection idle (in ms)   |




---
------ [HOME](../readme.md) --------------------------------------------

# Duct handler.sql

[![Build Status](https://travis-ci.org/duct-framework/handler.sql.svg?branch=master)](https://travis-ci.org/duct-framework/handler.sql)

A [Duct][] library that provides a way of constructing simple [Ring][]
handler functions that execute SQL expressions.

[duct]: https://github.com/duct-framework/duct
[ring]: https://github.com/ring-clojure/ring

## Installation

To install, add the following to your project `:dependencies`:

    [duct/handler.sql "0.2.0"]

## Usage

This library is designed to be used with a routing library with Duct
bindings, such as [Ataraxy][].

[ataraxy]: https://github.com/duct-framework/module.ataraxy

### Querying

Querying the database generally follows a HTTP `GET` request. There
are two keys for creating handlers that query the database:

* `:duct.handler.sql/query`     - for multiple results
* `:duct.handler.sql/query-one` - for when you have only one result

The simplest usage is a handler that queries the database the same way
each time:

```edn
{[:duct.handler.sql/query :example.handler.product/list]
 {:db  #ig/ref :duct.database/sql
  :sql ["SELECT * FROM products"]}}
```

In the above example, a [composite key][] is used to provide a unique
identifier for the handler.

The `:db` option should be a `duct.database.sql.Boundary` record, and
the `:query` option should be a [clojure.java.jdbc][] query vector.

If you want to change the query based on the request, you can
destructure the parameters you want in the `:request` option:

```edn
{[:duct.handler.sql/query-one :example.handler.product/find]
 {:db      #ig/ref :duct.database/sql
  :request {{:keys [id]} :route-params}
  :sql     ["SELECT * FROM products WHERE id = ?" id]}}
```

The response can also be altered. The `:rename` option is available
for renaming keys returned in the result set:

```edn
{[:duct.handler.sql/query :example.handler.product/list]
 {:db     #ig/ref :duct.database/sql
  :sql    ["SELECT id, name FROM products"]
  :rename {:id :product/id, :name :product/name}}}
```

The `:hrefs` option adds hypertext references based on [URI
Templates][]:

```edn
{[:duct.handler.sql/query :example.handler.product/list]
 {:db    #ig/ref :duct.database/sql
  :sql   ["SELECT id, name FROM products"]
  :hrefs {:self "/products{/id}"}}}
```

The `:hrefs` key takes template parameters from the result fields, and
from the requst destructuring.

Finally, the `:remove` key allows keys to be removed from the
response. This is useful if you want a key to be used in a href, but
not to show up in the final response:

```edn
{[:duct.handler.sql/query :example.handler.product/list]
 {:db     #ig/ref :duct.database/sql
  :sql    ["SELECT id, name FROM products"]
  :hrefs  {:self "/products{/id}"}
  :remove [:id]}}
```

[composite key]:     https://github.com/weavejester/integrant#composite-keys
[clojure.java.jdbc]: https://github.com/clojure/java.jdbc
[uri templates]:     https://tools.ietf.org/html/rfc6570

### Updating

Sometimes a HTTP request will alter the database. There are two keys
for creating handlers that update the database:

* `:duct.handler.sql/insert`  - for inserting rows
* `:duct.handler.sql/execute` - for updating or deleting rows

The `:duct.handler.sql/insert` key is designed to respond to a HTTP
`POST` event and send a "Created" 201 response with a "Location"
header created from the generated ID of an `INSERT`. For example:

```edn
{[:duct.handler.sql/insert :example.handler.product/create]
 {:db       #ig/ref :duct.database/sql
  :request  {{:strs [name]} :form-params}
  :sql      ["INSERT INTO products (name) VALUES (?)" name]
  :location "/products{/last_insert_rowid}"}}
```

The generated ID is returned differently depending on the database
being used. For [SQLite][], the ID is returned in the
`last_insert_rowid()` field. Because `()` are not valid characters in
URI templates, these are removed when the field name is sanitized.

The `:duct.handler.sql/execute` doesn't have to worry about generated
keys; it's designed to report to HTTP `DELETE` and `PUT` requests. If
the SQL updates one or more rows, a "No Content" 204 response is
returned, otherwise, if zero rows are updated, a 404 response is
returned.

For example:

```edn
{[:duct.handler.sql/execute :example.handler.product/update]
 {:db       #ig/ref :duct.database/sql
  :request  {{:keys [id]} :route-params, {:strs [name]} :form-params}
  :sql      ["UPDATE products SET name = ? WHERE id = ?" name id]}
  
 [:duct.handler.sql/execute :example.handler.product/destroy]
 {:db       #ig/ref :duct.database/sql
  :request  {{:keys [id]} :route-params}
  :sql      ["DELETE FROM products WHERE id = ?" id]}}
```

[sqlite]: https://sqlite.org/

### Full Example

Together with a router like Ataraxy, the configuration might look
something like:

```edn
{:duct.core/project-ns example
 :duct.core/environment :production

 :duct.module.web/api {}
 :duct.module/sql     {}

 :duct.module/ataraxy
 {"/products"
  {[:get]        [:product/list]
   [:get "/" id] [:product/find ^uuid id]
   
   [:post {{:strs [name]} :form-params}]
   [:product/create name]
   
   [:put "/" id {{:strs [name]} :form-params}]
   [:product/update ^uuid id name]
   
   [:delete "/" id]
   [:product/destroy ^uuid id]}}

 [:duct.handler.sql/query :example.handler.product/list]
 {:db    #ig/ref :duct.database/sql
  :query ["SELECT * FROM products"]}

 [:duct.handler.sql/query-one :example.handler.product/find]
 {:db      #ig/ref :duct.database/sql
  :request {[_ id] :ataraxy/result}
  :query   ["SELECT * FROM products WHERE id = ?" id]}
  
 {[:duct.handler.sql/insert :example.handler.product/create]
 {:db       #ig/ref :duct.database/sql
  :request  {[_ name] :ataraxy/result}
  :sql      ["INSERT INTO products (name) VALUES (?)" name]
  :location "/products{/last_insert_rowid}"}}

{[:duct.handler.sql/execute :example.handler.product/update]
 {:db       #ig/ref :duct.database/sql
  :request  {[_ id name] :ataraxy/result}
  :sql      ["UPDATE products SET name = ? WHERE id = ?" name id]}
  
 [:duct.handler.sql/execute :example.handler.product/destroy]
 {:db       #ig/ref :duct.database/sql
  :request  {[_ id] :ataraxy/result}
  :sql      ["DELETE FROM products WHERE id = ?" id]}}}
```


## Caveats

This library can produce simple handlers that require only the
information present in the request map. When paired with a good
routing library, this can be surprisingly powerful.

However, don't overuse this library. If your requirements for a
handler are more complex, then create your own `init-key` method for
the handler. It's entirely possible, even likely, that your app will
contain handlers created via this library, and handlers that are
created through your own `init-key` methods.

## License

Copyright © 2017 James Reeves

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

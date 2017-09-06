# Duct handler.sql

A [Duct][] library that provides a way of constructing simple [Ring][]
handler functions that execute SQL expressions.

[duct]: https://github.com/duct-framework/duct
[ring]: https://github.com/ring-clojure/ring

## Installation

To install, add the following to your project `:dependencies`:

    [duct/handler.sql "0.1.0-SNAPSHOT"]

## Usage

This library is designed to be used with a routing library with Duct
bindings, such as [Ataraxy][].

The simplest usage is a handler that queries the database the same way
each time:

```edn
{[:duct.handler.sql/select :example.handler.product/list]
 {:db    #ig/ref :duct.database/sql
  :query ["SELECT * FROM products"]}}
```

In the above example, a [composite key][] is used to provide a unique
identifier for the handler.

If you want to change the query based on the request, you can
destructure the parameters you want:

```edn
{[:duct.handler.sql/select-one :example.handler.product/find]
 {:db      #ig/ref :duct.database/sql
  :request {{:keys [id]} :route-params}
  :query   ["SELECT * FROM products WHERE id = ?" id]}}
```

There are currently two different types of handler key:

* `:duct.handler.sql/select`     - for multiple results
* `:duct.handler.sql/select-one` - for when you have only one result

Together with a router, the configuration might look something like:

```edn
{:duct.core/project-ns example
 :duct.core/environment :production

 :duct.module.web/api {}
 :duct.module/sql     {}

 :duct.moduke/ataraxy
 {[:get "/products"]     [:product/list]
  [:get "/products/" id] [:product/find ^uuid id]}

 [:duct.handler.sql/select :example.handler.product/list]
 {:db    #ig/ref :duct.database/sql
  :query ["SELECT * FROM products"]}

 [:duct.handler.sql/select-one :example.handler.product/find]
 {:db      #ig/ref :duct.database/sql
  :request {[_ id] :ataraxy/result}
  :query   ["SELECT * FROM products WHERE id = ?" id]}}
```

[ataraxy]: https://github.com/duct-framework/module.ataraxy
[composite key]: https://github.com/weavejester/integrant#composite-keys

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

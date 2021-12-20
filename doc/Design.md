# MobShop

Collaborative shopping list

---

This document describes the [Features](#Features) and [Architecture](#Architecture) of the app to be developed.

See [README](../README.md) for an outline of the app.

---

##Features


## Architecture


```kotlin
val dataSource: MobShopItems() {
}
```

```plantuml
@startuml component
component activity_auth
component activity_mobs
component activity_mobList
component activity_shop
actor mobber
node app
database data

mobber -> app
data <-> app
app -> activity_auth
activity_auth <-> activity_mobs
activity_mobs -> activity_mobList
activity_mobList -> activity_shop
@enduml
```

![workflow test](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/fwornle/MobShop/master/doc/puml/workflow.puml)

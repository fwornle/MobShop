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
actor client
node app
database db

db -> app
app -> client
@enduml
```

![](http://www.plantuml.com/plantuml/proxy?src=https://raw.github.com/plantuml/pl)
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

![system undervie](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://github.com/fwornle/MobShop/blob/master/doc/test.puml)
![system overview](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.github.com/anoff/plantbuddy/master/assets/overview.iuml)
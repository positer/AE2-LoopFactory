---
navigation:
  parent: ae2:items-blocks-machines/items-blocks-machines-index.md
  title: Omnidirectional Connector
  icon: ae2lightoptimizer:omnidirectional_connector
  position: 920
categories:
- devices
item_ids:
- ae2lightoptimizer:omnidirectional_connector
---

# Omnidirectional Connector

<BlockImage id="ae2lightoptimizer:omnidirectional_connector" scale="6" />

<RecipeFor id="ae2lightoptimizer:omnidirectional_connector" />

Create or select a named link in the connector screen. All loaded connectors on that link share one native AE2 virtual node, including across dimensions. Each connector exposes the same rear connection and placement geometry as the wireless access point; all purple details, including chassis accents and particles, are white.

Connectors require no AE power and do not consume a device channel. They carry native AE2 channels, with the ordinary network channel limits still applied. A supercomputing interface doubles the native capacity once. Connectors do not keep chunks loaded; unloading or removing an endpoint removes its virtual connection.

Craft one wireless access point, one loop crystal and one quantum entangled singularity in any order to obtain four connectors. Dismantling with an AE2 wrench preserves the selected link in the returned connector item.


A linked, online connector emits slow outward white particles confined to its own block. Unlinking or going offline stops the effect. Online status follows AE2; this adds no energy demand.

## Deploying the wireless factory network

The connector's base (mounting) face may point in any direction. The factory network joins on the structure alone; the selected channel only carries the AE network.

| Placement | Factory wireless | AE network |
| --- | --- | --- |
| Base on a Loop Factory network terminal's **top** face | Yes: that terminal's factory network | A selected channel joins the terminal's network |
| Base on a factory pattern provider's **subnet (front) face** | Yes: the provider's subnet | A selected channel joins the provider's **main** network; the subnet never joins it |
| Base on any other face of a terminal or provider | No | The connector still joins its channel through the base face |

1. Place the terminal or provider first, then place the connector so its base face rests on the matching face.
2. A white ring of particles orbiting inside the connector block means the factory structure is formed.
3. Open the connector screen to select a channel when the AE network should also be carried. Without a channel the machine's AE network is left untouched.
4. The connector's chunk becomes the wireless range: every machine in it can be discovered and tagged by that factory network, from any mod. Loop Factory terminals and providers are excluded and must be cabled.
5. Nothing is scanned, joined or moved before a tag binds a machine.

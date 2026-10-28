# Single-use attached worker registration

A short attached worker can finish before its provisioning thread has finished.
Previously DockerCloud completed its planned-node future and then independently
registered the node, while Jenkins core also registered that completed future.
If the first consumer registered and removed the worker before the provisioning
thread continued, the late registration recreated its Node.

Core also publishes a Computer before saving its Node. Task admission now waits
until a registered node's first save succeeds, so a fast job cannot remove its
configuration directory during that save. Unregistered property saves and
BulkChange-deferred saves do not open admission. The transient gate does not
change persisted task-acceptance settings or require restored nodes to save again.
Core owns completed planned-node registration. Successful delivery immediately
requests a core provisioning review. Even a notification Error does not transfer
cleanup ownership back to the producer after delivery succeeds. This prevents a
ready but unregistered container
from waiting until the next periodic review and being mistaken for an orphan.
The planned node's spent hook
requests cleanup of its owned Docker container if registration did not persist
successfully or the worker is already absent. Core invokes this hook after both
checked and unchecked registration failures. Provisioning failures before a node
is delivered retain the provisioning task's existing cleanup ownership. If the
future was cancelled before delivery, the provisioning task cleans up the newly
created worker rather than handing ownership to a future core cannot consume.

## Minimal reproduction

Run `mvn -Dtest=DockerProvisioningLifecycleTest test` in the plugin checkout.
The tests use isolated Jenkins Test Harness instances and mock container commands;
they require no Docker daemon, repository credentials or external services.
The future-consumption test registers and removes the returned Node inline before
the provisioning thread can continue. A second test pauses initial persistence
and checks task admission while core has already published that Node.
Additional checks exercise prompt consumption by core, cancellation ownership,
core's actual checked and unchecked failure consumer,
owned-container cleanup, failed persistence, restored
acceptance settings, unregistered saves and deferred BulkChange persistence.

These tests exercise the relevant boundaries deterministically. They do not
reconstruct every thread interleaving or the exact atomic-save exception seen in
a real Docker incident. Native attached-worker tests remain necessary.

## Scope and remaining boundary

This correction targets cloud workers using attached or SSH connections, which
rely on core for their initial registration. The inbound/JNLP connector registers
a Node earlier, before starting its container. That preregistration can open
admission before the planned node reaches core; the separate inbound resurrection
scenario remains unresolved by this change. No claim of a universal connector
lifecycle fix is made. Explicit dockerNode registration remains supported.

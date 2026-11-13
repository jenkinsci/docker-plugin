# Connecting workers and transient excess capacity

A single-use Docker worker can finish while another job remains queued. The next
worker is registered with Jenkins before its channel has connected. At this point
core's standard provisioning strategy combines a current queue snapshot with
smoothed connecting/planned capacity. If the connecting capacity average has not
caught up, core can allocate an additional worker even though a connecting
executor already covers that job. This is distinct from registering a completed
worker again.

The isolated HomeLab trace on Jenkins 2.568.3 had queue length 1, connecting
executors 1, no online executors, queue estimate 0.729 and planned estimate 0.066.
The standard strategy computed excess 0.663, above its 0.5 threshold, and requested
a second replacement. The cloud cap of two remained enforced, but one of the two
replacement containers never received a build.

The Docker strategy now has ordinal 1, ahead of core's ordinal 0 standard
strategy. It accounts for current available and connecting executors, pending
launches and capacity recorded by earlier strategies in the same review. It
counts executors returned by provisioning rather than assuming one executor per
planned node. It also consults the standard provisioning listener veto hook
before requesting new workers. When Docker cannot meet the demand, or has no
compatible cloud, subsequent strategies retain control.

This prioritizes eligible Docker clouds before the standard strategy. On
controllers with several different cloud providers, use explicit/exclusive
labels where cloud selection order matters. Higher-ordinal third-party
strategies can still run first. This change does not create a warm pool, change
retention timeouts or change container caps. It does not promise that every
possible queue cancellation or asynchronous change can avoid unused allocations.

Regression tests exercise the actual installed strategy order with the captured
snapshot/EMA values, existing additional planned capacity, multi-executor
responses, provisioning vetoes, insufficient Docker capacity and another cloud
type. No Docker daemon or timing sleeps are needed for these tests.

This is a separate local follow-up to the single-use registration change. Native
comparison results and review records are kept in the HomeLab task evidence;
publication to GitHub requires the operator's approval.

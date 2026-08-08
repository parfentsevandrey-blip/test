"""The operator's dashboard: a control surface, not a status page.

This package exists because the earning half needs a human in the loop for the
decisions it is not allowed to make alone — approving work claimed in your name,
minting an invoice, deciding a gig is worth the hours. A terminal is a poor place
to do that; a local page is a good one.

It is deliberately small and deliberately local. The UI can approve work and
issue payment instructions, so it is treated as privileged: loopback bind, a
token on every API call, no cross-origin access. Exposing it to a network is a
conscious act that needs TLS in front of it.

The invariant the whole surface protects is the project's load-bearing rule:
confirmed on-chain income and expected pipeline value travel in separate fields,
all the way to the browser, so nothing downstream can add one into the other.
"""

from __future__ import annotations

from .api import AgentContext, ApiHandler, serve

__all__ = ["AgentContext", "ApiHandler", "serve"]

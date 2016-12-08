Consensus and Notaries
======================

Consensus over transaction validity is performed only by parties to the transaction in question. Therefore, data is only
shared with those parties which are required to see it. Other platforms generally reach consensus at the ledger level.
Thus, any given actor in a Corda system sees only a subset of the overall data managed by the system as a whole.
We say a piece of data is “on-ledger” if at least two actors on the system are in consensus as to its existence and
details and we allow arbitrary combinations of actors to participate in the consensus process for any given piece of data.
Data held by only one actor is “off-ledger”.

The following diagram illustrates this model:

.. image:: whitepaper/images/Consensus.png
   :scale: 50 %
   :align: center


Corda has the "pluggable" notary services which provide transaction ordering and timestamping services. This is to improve
privacy, scalability, legal-system compatibility and algorithmic agility. A single service may be composed of many mutually
untrusting nodes coordinating via a byzantine fault tolerant algorithm, or could be very simple, like a single machine.
In some cases, like when evolving a state requires the signatures of all relevant parties, there may be no need for a uniqueness service at all.

Notaries are expected to be composed of multiple mutually distrusting parties who use a standard consensus algorithm.
Notaries are identified by and sign with composite public keys. Notaries accept transactions submitted to them for processing
and either return a signature over the transaction, or a rejection error that states that a double spend has occurred.
The presence of a notary signature from the state’s chosen notary indicates transaction finality. An app developer triggers
notarisation by invoking the ``FinalityFlow`` on the transaction once all other necessary signatures have been gathered.
Once the finality flow returns successfully, the transaction can be considered committed to the database.

Consensus is described in detail :doc:`here </consensus>`

Additionally, section 7 of the `Technical white paper`_ covers this topic in significant more depth.

.. _`Technical white paper`: _static/corda-technical-whitepaper.pdf


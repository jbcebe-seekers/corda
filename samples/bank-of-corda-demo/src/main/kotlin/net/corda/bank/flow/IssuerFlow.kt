package net.corda.bank.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.FungibleAsset
import net.corda.core.contracts.Issued
import net.corda.core.contracts.issuedBy
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.node.NodeInfo
import net.corda.core.node.PluginServiceHub
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.flows.CashCommand
import net.corda.flows.CashFlow
import net.corda.flows.CashFlowResult
import java.util.*

/**
 *  This flow enables a client to request issuance of some [FungibleAsset] from a
 *  server acting as an issuer (see [Issued]) of FungibleAssets
 *
 */
object IssuerFlow {
    data class IssuanceRequestState(val amount: Amount<Currency>, val issueToParty: Party, val issuerPartyRef: OpaqueBytes)

    /*
     * IssuanceRequester refers to a Node acting as issuance requester of some FungibleAsset
     */
    class IssuanceRequester(val amount: Amount<Currency>, val issueToPartyName: String, val issueToPartyRef: OpaqueBytes,
                            val otherParty: String): FlowLogic<IssuerFlowResult>() {
        @Suspendable
        override fun call(): IssuerFlowResult {
            val issueToParty = serviceHub.identityService.partyFromName(issueToPartyName)
            val bankOfCordaParty = serviceHub.identityService.partyFromName(otherParty)
            if (issueToParty == null || bankOfCordaParty == null) {
                return IssuerFlowResult.Failed("Unable to locate ${otherParty} in Network Map Service")
            }
            else {
                val issueRequest = IssuanceRequestState(amount, issueToParty, issueToPartyRef)
                return sendAndReceive<IssuerFlowResult>(bankOfCordaParty, issueRequest).unwrap { it }
            }
        }
    }

    /*
     * Issuer refers to a Node acting as a Bank Issuer of FungibleAssets
     */
    class Issuer(val otherParty: Party,
                 override val progressTracker: ProgressTracker = Issuer.tracker()): FlowLogic<IssuerFlowResult>() {
        companion object {
            object AWAITING_REQUEST : ProgressTracker.Step("Awaiting issuance request")
            object ISSUING : ProgressTracker.Step("Self issuing asset")
            object TRANSFERRING : ProgressTracker.Step("Transferring asset to issuance requester")
            object SENDING_CONIFIRM : ProgressTracker.Step("Confirming asset issuance to requester")
            fun tracker() = ProgressTracker(AWAITING_REQUEST, ISSUING, TRANSFERRING, SENDING_CONIFIRM)
        }

        @Suspendable
        override fun call(): IssuerFlowResult {
            progressTracker.currentStep = AWAITING_REQUEST
            val issueRequest = receive<IssuanceRequestState>(otherParty).unwrap { it }
            // TODO: parse request to determine Asset to issue
            try {
                val result = issueCashTo(issueRequest.amount, issueRequest.issueToParty, issueRequest.issuerPartyRef)
                val response = if (result is CashFlowResult.Success)
                    IssuerFlowResult.Success(result.transaction!!.tx.id, "Amount ${issueRequest.amount} issued to ${issueRequest.issueToParty}")
                else
                    IssuerFlowResult.Failed((result as CashFlowResult.Failed).message)
                progressTracker.currentStep = SENDING_CONIFIRM
                send(otherParty, response)
                return response
            }
            catch(ex: Exception) {
                return IssuerFlowResult.Failed(ex.message)
            }
        }

        @Suspendable
        private fun issueCashTo(amount: Amount<Currency>,
                                issueTo: Party, issuerPartyRef: OpaqueBytes): CashFlowResult {
            // TODO: we should pass in the Notary to be used
            val notaryNode: NodeInfo = serviceHub.networkMapCache.notaryNodes[0]
            // invoke Cash subflow to issue Asset
            progressTracker.currentStep = ISSUING
            val bankOfCordaParty = serviceHub.myInfo.legalIdentity
            val issueCashFlow = CashFlow(CashCommand.IssueCash(
                    amount, issuerPartyRef, bankOfCordaParty, notaryNode.notaryIdentity))
            val resultIssue = subFlow(issueCashFlow)
            // NOTE: issueCashFlow performs a Broadcast (which stores a local copy of the txn to the ledger)
            // TODO: use Exception propagation to handle failed sub flow execution
            if (resultIssue is CashFlowResult.Failed) {
                logger.error("Problem issuing cash: ${resultIssue.message}")
                return resultIssue
            }
            // now invoke Cash subflow to Move issued assetType to issue requester
            progressTracker.currentStep = TRANSFERRING
            val moveCashFlow = CashFlow(CashCommand.PayCash(
                    amount.issuedBy(bankOfCordaParty.ref(issuerPartyRef)), issueTo))
            val resultMove = subFlow(moveCashFlow)
            // NOTE: CashFlow PayCash calls FinalityFlow which performs a Broadcast (which stores a local copy of the txn to the ledger)
            // TODO: use Exception propagation to handle failed sub flow execution
            if (resultMove is CashFlowResult.Failed) {
                logger.error("Problem transferring cash: ${resultMove.message}")
                return resultMove
            }
            return resultMove
        }

        class Service(services: PluginServiceHub) {
            init {
                services.registerFlowInitiator(IssuanceRequester::class) {
                    Issuer(it)
                }
            }
        }
    }
}

sealed class IssuerFlowResult {
    /**
     * @param txnId returned as a result, in the case where the flow completed successfully.
     */
    class Success(val txnId: SecureHash, val message: String?) : IssuerFlowResult() {
        override fun toString() = "Issuer Success($message)"

        override fun equals(other: Any?): Boolean {
            return other is Success &&
                    this.txnId == other.txnId &&
                    this.message.equals(other.message)
        }

        override fun hashCode(): Int {
            var result = txnId.hashCode()
            result = 31 * result + (message?.hashCode() ?: 0)
            return result
        }
    }

    class Failed(val message: String?) : IssuerFlowResult() {
        override fun toString() = "Issuer failed($message)"
    }
}

package net.corda.traderdemo.api

import net.corda.bank.api.BOC_ISSUER_PARTY
import net.corda.bank.flow.IssuerFlow.IssuanceRequester
import net.corda.bank.flow.IssuerFlowResult
import net.corda.core.contracts.DOLLARS
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.Emoji
import net.corda.core.utilities.loggerFor
import net.corda.traderdemo.flow.SellerFlow
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import kotlin.test.assertEquals

// API is accessible from /api/traderdemo. All paths specified below are relative to it.
@Path("traderdemo")
class TraderDemoApi(val services: ServiceHub) {
    data class TestCashParams(val amount: Int, val notary: String)
    data class SellParams(val amount: Int)
    private companion object {
        val logger = loggerFor<TraderDemoApi>()
    }

    /**
     * Self issue some cash.
     * TODO: At some point this demo should be extended to have a central bank node.
     */
    @PUT
    @Path("create-test-cash")
    @Consumes(MediaType.APPLICATION_JSON)
    fun createTestCash(params: TestCashParams): Response {
        val notary = services.networkMapCache.notaryNodes.single { it.legalIdentity.name == params.notary }.notaryIdentity

        val result = services.invokeFlowAsync(IssuanceRequester::class.java, params.amount.DOLLARS,
                                              services.myInfo.legalIdentity.name, OpaqueBytes.of(1), BOC_ISSUER_PARTY.name)
        if (result.resultFuture.get() is IssuerFlowResult.Success) {
            logger.info("Issue request completed successfully: ${params}")
            return Response.status(Response.Status.CREATED).build()
        } else {
            return Response.status(Response.Status.BAD_REQUEST).build()
        }

        return Response.status(Response.Status.CREATED).build()
    }

    @POST
    @Path("{party}/sell-cash")
    @Consumes(MediaType.APPLICATION_JSON)
    fun sellCash(params: SellParams, @PathParam("party") partyName: String): Response {
        val otherParty = services.identityService.partyFromName(partyName)
        if (otherParty != null) {
            // The seller will sell some commercial paper to the buyer, who will pay with (self issued) cash.
            //
            // The CP sale transaction comes with a prospectus PDF, which will tag along for the ride in an
            // attachment. Make sure we have the transaction prospectus attachment loaded into our store.
            //
            // This can also be done via an HTTP upload, but here we short-circuit and do it from code.
            if (services.storageService.attachments.openAttachment(SellerFlow.PROSPECTUS_HASH) == null) {
                javaClass.classLoader.getResourceAsStream("bank-of-london-cp.jar").use {
                    val id = services.storageService.attachments.importAttachment(it)
                    assertEquals(SellerFlow.PROSPECTUS_HASH, id)
                }
            }

            // The line below blocks and waits for the future to resolve.
            val stx = services.invokeFlowAsync<SignedTransaction>(SellerFlow::class.java, otherParty, params.amount.DOLLARS).resultFuture.get()
            logger.info("Sale completed - we have a happy customer!\n\nFinal transaction is:\n\n${Emoji.renderIfSupported(stx.tx)}")
            return Response.status(Response.Status.OK).build()
        } else {
            return Response.status(Response.Status.BAD_REQUEST).build()
        }
    }
}

/**
 * 
 */
package org.mobicents.jss7.standalone.example.ussd;

import org.apache.log4j.Logger;
import org.mobicents.protocols.api.IpChannelType;
import org.mobicents.protocols.sctp.ManagementImpl;
import org.mobicents.protocols.ss7.m3ua.Asp;
import org.mobicents.protocols.ss7.m3ua.ExchangeType;
import org.mobicents.protocols.ss7.m3ua.Functionality;
import org.mobicents.protocols.ss7.m3ua.IPSPType;
import org.mobicents.protocols.ss7.m3ua.impl.M3UAManagementImpl;
import org.mobicents.protocols.ss7.m3ua.parameter.RoutingContext;
import org.mobicents.protocols.ss7.m3ua.parameter.TrafficModeType;
import org.mobicents.protocols.ss7.map.MAPStackImpl;
import org.mobicents.protocols.ss7.map.api.*;
import org.mobicents.protocols.ss7.map.api.datacoding.CBSDataCodingScheme;
import org.mobicents.protocols.ss7.map.api.dialog.*;
import org.mobicents.protocols.ss7.map.api.errors.MAPErrorMessage;
import org.mobicents.protocols.ss7.map.api.primitives.*;
import org.mobicents.protocols.ss7.map.api.service.supplementary.*;
import org.mobicents.protocols.ss7.map.datacoding.CBSDataCodingSchemeImpl;
import org.mobicents.protocols.ss7.sccp.impl.SccpResourceImpl;
import org.mobicents.protocols.ss7.sccp.impl.SccpStackImpl;
import org.mobicents.protocols.ss7.tcap.TCAPStackImpl;
import org.mobicents.protocols.ss7.tcap.api.TCAPStack;
import org.mobicents.protocols.ss7.tcap.asn.ApplicationContextName;
import org.mobicents.protocols.ss7.tcap.asn.comp.Problem;

import java.nio.charset.Charset;

/**
 * @author amit bhayani
 * 
 */
public class SctpClient extends AbstractSctpBase {

	private static Logger logger = Logger.getLogger(SctpClient.class);

	// SCTP
	private ManagementImpl sctpManagement;

	// M3UA
	private M3UAManagementImpl clientM3UAMgmt;

	// SCCP
	private SccpStackImpl sccpStack;
	private SccpResourceImpl sccpResource;

	// TCAP
	private TCAPStack tcapStack;

	// MAP
	private MAPStackImpl mapStack;
	private MAPProvider mapProvider;

	/**
	 * 
	 */
	public SctpClient() {
		// TODO Auto-generated constructor stub
	}

	protected void initializeStack(IpChannelType ipChannelType) throws Exception {

		this.initSCTP(ipChannelType);

		// Initialize M3UA first
		this.initM3UA();

		// Initialize SCCP
		this.initSCCP();

		// Initialize TCAP
		this.initTCAP();

		// Initialize MAP
		this.initMAP();

		// FInally start ASP
		// Set 5: Finally start ASP
		this.clientM3UAMgmt.startAsp("ASP1");
	}

	private void initSCTP(IpChannelType ipChannelType) throws Exception {
		logger.debug("Initializing SCTP Stack ....");
		this.sctpManagement = new ManagementImpl("Client");
		this.sctpManagement.setSingleThread(true);
		this.sctpManagement.setConnectDelay(10000);
		this.sctpManagement.start();
		this.sctpManagement.removeAllResourses();

		// 1. Create SCTP Association
		sctpManagement.addAssociation(CLIENT_IP, CLIENT_PORT, SERVER_IP, SERVER_PORT, CLIENT_ASSOCIATION_NAME,
				ipChannelType, null);
		logger.debug("Initialized SCTP Stack ....");
	}

	private void initM3UA() throws Exception {
		logger.debug("Initializing M3UA Stack ....");
		this.clientM3UAMgmt = new M3UAManagementImpl("Client");
		this.clientM3UAMgmt.setTransportManagement(this.sctpManagement);
		this.clientM3UAMgmt.start();
		// this.clientM3UAMgmt.removeAllResourses();

		// m3ua as create rc <rc> <ras-name>
		RoutingContext rc = factory.createRoutingContext(new long[] { 100l });
		TrafficModeType trafficModeType = factory.createTrafficModeType(TrafficModeType.Loadshare);
		this.clientM3UAMgmt.createAs("AS1", Functionality.AS, ExchangeType.SE, IPSPType.CLIENT, rc, trafficModeType,
				0, null);

		// Step 2 : Create ASP
		this.clientM3UAMgmt.createAspFactory("ASP1", CLIENT_ASSOCIATION_NAME);

		// Step3 : Assign ASP to AS
		Asp asp = this.clientM3UAMgmt.assignAspToAs("AS1", "ASP1");

		// Step 4: Add Route. Remote point code is 2
		clientM3UAMgmt.addRoute(SERVET_SPC, -1, -1, "AS1");
		logger.debug("Initialized M3UA Stack ....");

	}

	private void initSCCP() throws Exception {
		logger.debug("Initializing SCCP Stack ....");
		this.sccpStack = new SccpStackImpl("MapLoadClientSccpStack");
		this.sccpStack.setMtp3UserPart(1, this.clientM3UAMgmt);

		this.sccpStack.start();
		this.sccpStack.removeAllResourses();

		//RemoteSignalingPointCode rspc = new RemoteSignalingPointCodeImpl(SERVET_SPC, 0, 0);
		//RemoteSubSystem rss = new RemoteSubSystemImpl(SERVET_SPC, SSN, 0, false);
		this.sccpStack.getSccpResource().addRemoteSpc(0, SERVET_SPC, 0, 0);
		this.sccpStack.getSccpResource().addRemoteSsn(0, SERVET_SPC, SSN, 0, false);

		//Mtp3ServiceAccessPoint sap = new Mtp3ServiceAccessPoint(1, CLIENT_SPC, NETWORK_INDICATOR);
		//Mtp3Destination dest = new Mtp3Destination(SERVET_SPC, SERVET_SPC, 0, 255, 255);
		this.sccpStack.getRouter().addMtp3ServiceAccessPoint(1, 1, CLIENT_SPC, NETWORK_INDICATOR);
		this.sccpStack.getRouter().addMtp3Destination(1, 1, SERVET_SPC, SERVET_SPC, 0, 255, 255);
		logger.debug("Initialized SCCP Stack ....");
	}

	private void initTCAP() throws Exception {
		logger.debug("Initializing TCAP Stack ....");
		this.tcapStack = new TCAPStackImpl("TCAPStack", this.sccpStack.getSccpProvider(), SSN);
		this.tcapStack.setDialogIdleTimeout(60000);
		this.tcapStack.setInvokeTimeout(30000);
		this.tcapStack.setMaxDialogs(2000);
		this.tcapStack.start();
		logger.debug("Initialized TCAP Stack ....");
	}

	private void initMAP() throws Exception {
		logger.debug("Initializing MAP Stack ....");
		// this.mapStack = new MAPStackImpl(this.sccpStack.getSccpProvider(),
		// SSN);
		this.mapStack = new MAPStackImpl("MAPStack", this.tcapStack.getProvider());
		this.mapProvider = this.mapStack.getMAPProvider();

		this.mapProvider.addMAPDialogListener(this);
		this.mapProvider.getMAPServiceSupplementary().addMAPServiceListener(this);

		this.mapProvider.getMAPServiceSupplementary().acivate();

		this.mapStack.start();
		logger.debug("Initialized MAP Stack ....");
	}

    private void initiateUSSD() throws MAPException {

        // First create Dialog
        /*MAPDialogSupplementary mapDialog = this.mapProvider.getMAPServiceSupplementary().createNewDialog(
				MAPApplicationContext.getInstance(MAPApplicationContextName.networkUnstructuredSsContext,
						MAPApplicationContextVersion.version2), SCCP_CLIENT_ADDRESS, null, SCCP_SERVER_ADDRESS, null);*/

        MAPParameterFactory mapParameterFactory = this.mapProvider.getMAPParameterFactory();

        // Originating address but its value is to be carried at MAP level.
        // Mobile Country Code (MCC): Brazil (Federative Republic of) (724)
        // Mobile Network Code (MNC): CTBC CELULAR R I (34)
        ISDNAddressString origReference = mapParameterFactory.createISDNAddressString(AddressNature.international_number, NumberingPlan.land_mobile, "72434");

        // Destination address but its value is to be carried at MAP level.
        // Mobile Country Code (MCC): Germany (Federal Republic of) (262)
        //Mobile Network Code (MNC): Vodafone D2 GmbH (02)
        ISDNAddressString destReference = mapParameterFactory.createISDNAddressString(AddressNature.international_number, NumberingPlan.land_mobile, "26202");

        MAPDialogSupplementary mapDialog = this.mapProvider.getMAPServiceSupplementary()
                .createNewDialog(MAPApplicationContext.getInstance(MAPApplicationContextName.networkUnstructuredSsContext,
                        MAPApplicationContextVersion.version2), SCCP_CLIENT_ADDRESS, origReference, SCCP_SERVER_ADDRESS, destReference);


        CBSDataCodingScheme ussdDataCodingScheme = new CBSDataCodingSchemeImpl(0x0f);

        // USSD String: *125*+31628839999#
        // The Charset is null, here we let system use default Charset (UTF-7 as
        // explained in GSM 03.38. However if MAP User wants, it can set its own
        // impl of Charset
        USSDString ussdString = this.mapProvider.getMAPParameterFactory().createUSSDString("*125*+31628839999#");

        ISDNAddressString msisdn = this.mapProvider.getMAPParameterFactory().createISDNAddressString(
                AddressNature.international_number, NumberingPlan.ISDN, "553491756709");

        mapDialog.addProcessUnstructuredSSRequest(ussdDataCodingScheme, ussdString, null, msisdn);

        // This will initiate the TC-BEGIN with INVOKE component
        mapDialog.send();
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.map.api.MAPDialogListener#onDialogAccept(
	 * org.mobicents.protocols.ss7.map.api.MAPDialog,
	 * org.mobicents.protocols.ss7.map.api.primitives.MAPExtensionContainer)
	 */
	public void onDialogAccept(MAPDialog mapDialog, MAPExtensionContainer extensionContainer) {
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("onDialogAccept for DialogId=%d MAPExtensionContainer=%s",
					mapDialog.getLocalDialogId(), extensionContainer));
		}
	}

	public void onDialogReject(MAPDialog mapDialog, MAPRefuseReason mapRefuseReason, ApplicationContextName applicationContextName, MAPExtensionContainer mapExtensionContainer) {

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.map.api.MAPDialogListener#onDialogClose(org
	 * .mobicents.protocols.ss7.map.api.MAPDialog)
	 */
	public void onDialogClose(MAPDialog mapDialog) {
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("DialogClose for Dialog=%d", mapDialog.getLocalDialogId()));
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.map.api.MAPDialogListener#onDialogDelimiter
	 * (org.mobicents.protocols.ss7.map.api.MAPDialog)
	 */
	public void onDialogDelimiter(MAPDialog mapDialog) {
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("onDialogDelimiter for DialogId=%d", mapDialog.getLocalDialogId()));
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.map.api.MAPDialogListener#onDialogNotice(
	 * org.mobicents.protocols.ss7.map.api.MAPDialog,
	 * org.mobicents.protocols.ss7.map.api.dialog.MAPNoticeProblemDiagnostic)
	 */
	public void onDialogNotice(MAPDialog mapDialog, MAPNoticeProblemDiagnostic noticeProblemDiagnostic) {
		logger.error(String.format("onDialogNotice for DialogId=%d MAPNoticeProblemDiagnostic=%s ",
				mapDialog.getLocalDialogId(), noticeProblemDiagnostic));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.map.api.MAPDialogListener#onDialogProviderAbort
	 * (org.mobicents.protocols.ss7.map.api.MAPDialog,
	 * org.mobicents.protocols.ss7.map.api.dialog.MAPAbortProviderReason,
	 * org.mobicents.protocols.ss7.map.api.dialog.MAPAbortSource,
	 * org.mobicents.protocols.ss7.map.api.primitives.MAPExtensionContainer)
	 */
	public void onDialogProviderAbort(MAPDialog mapDialog, MAPAbortProviderReason abortProviderReason,
			MAPAbortSource abortSource, MAPExtensionContainer extensionContainer) {
		logger.error(String
				.format("onDialogProviderAbort for DialogId=%d MAPAbortProviderReason=%s MAPAbortSource=%s MAPExtensionContainer=%s",
                        mapDialog.getLocalDialogId(), abortProviderReason, abortSource, extensionContainer));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.map.api.MAPDialogListener#onDialogReject(
	 * org.mobicents.protocols.ss7.map.api.MAPDialog,
	 * org.mobicents.protocols.ss7.map.api.dialog.MAPRefuseReason,
	 * org.mobicents.protocols.ss7.map.api.dialog.MAPProviderError,
	 * org.mobicents.protocols.ss7.tcap.asn.ApplicationContextName,
	 * org.mobicents.protocols.ss7.map.api.primitives.MAPExtensionContainer)
	 */
	public void onDialogReject(MAPDialog mapDialog, MAPRefuseReason refuseReason, MAPProviderAbortReason providerError,
			ApplicationContextName alternativeApplicationContext, MAPExtensionContainer extensionContainer) {
		logger.error(String
				.format("onDialogReject for DialogId=%d MAPRefuseReason=%s MAPProviderError=%s ApplicationContextName=%s MAPExtensionContainer=%s",
                        mapDialog.getLocalDialogId(), refuseReason, providerError, alternativeApplicationContext,
                        extensionContainer));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.map.api.MAPDialogListener#onDialogRelease
	 * (org.mobicents.protocols.ss7.map.api.MAPDialog)
	 */
	public void onDialogRelease(MAPDialog mapDialog) {
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("onDialogResease for DialogId=%d", mapDialog.getLocalDialogId()));
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.map.api.MAPDialogListener#onDialogRequest
	 * (org.mobicents.protocols.ss7.map.api.MAPDialog,
	 * org.mobicents.protocols.ss7.map.api.primitives.AddressString,
	 * org.mobicents.protocols.ss7.map.api.primitives.AddressString,
	 * org.mobicents.protocols.ss7.map.api.primitives.MAPExtensionContainer)
	 */
	public void onDialogRequest(MAPDialog mapDialog, AddressString destReference, AddressString origReference,
			MAPExtensionContainer extensionContainer) {
		if (logger.isDebugEnabled()) {
			logger.debug(String
					.format("onDialogRequest for DialogId=%d DestinationReference=%s OriginReference=%s MAPExtensionContainer=%s",
                            mapDialog.getLocalDialogId(), destReference, origReference, extensionContainer));
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.map.api.MAPDialogListener#onDialogRequestEricsson
	 * (org.mobicents.protocols.ss7.map.api.MAPDialog,
	 * org.mobicents.protocols.ss7.map.api.primitives.AddressString,
	 * org.mobicents.protocols.ss7.map.api.primitives.AddressString,
	 * org.mobicents.protocols.ss7.map.api.primitives.IMSI,
	 * org.mobicents.protocols.ss7.map.api.primitives.AddressString)
	 */
	public void onDialogRequestEricsson(MAPDialog mapDialog, AddressString destReference, AddressString origReference,
			IMSI arg3, AddressString arg4) {
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("onDialogRequest for DialogId=%d DestinationReference=%s OriginReference=%s ",
					mapDialog.getLocalDialogId(), destReference, origReference));
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.map.api.MAPDialogListener#onDialogTimeout
	 * (org.mobicents.protocols.ss7.map.api.MAPDialog)
	 */
	public void onDialogTimeout(MAPDialog mapDialog) {
		logger.error(String.format("onDialogTimeout for DialogId=%d", mapDialog.getLocalDialogId()));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.map.api.MAPDialogListener#onDialogUserAbort
	 * (org.mobicents.protocols.ss7.map.api.MAPDialog,
	 * org.mobicents.protocols.ss7.map.api.dialog.MAPUserAbortChoice,
	 * org.mobicents.protocols.ss7.map.api.primitives.MAPExtensionContainer)
	 */
	public void onDialogUserAbort(MAPDialog mapDialog, MAPUserAbortChoice userReason,
			MAPExtensionContainer extensionContainer) {
		logger.error(String.format("onDialogUserAbort for DialogId=%d MAPUserAbortChoice=%s MAPExtensionContainer=%s",
				mapDialog.getLocalDialogId(), userReason, extensionContainer));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.mobicents.protocols.ss7.map.api.service.supplementary.
	 * MAPServiceSupplementaryListener
	 * #onProcessUnstructuredSSRequest(org.mobicents
	 * .protocols.ss7.map.api.service
	 * .supplementary.ProcessUnstructuredSSRequest)
	 */
	public void onProcessUnstructuredSSRequest(ProcessUnstructuredSSRequest procUnstrReqInd) {
		// This error condition. Client should never receive the
		// ProcessUnstructuredSSRequestIndication
		logger.error(String.format("onProcessUnstructuredSSRequestIndication for Dialog=%d and invokeId=%d",
				procUnstrReqInd.getMAPDialog().getLocalDialogId(), procUnstrReqInd.getInvokeId()));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.mobicents.protocols.ss7.map.api.service.supplementary.
	 * MAPServiceSupplementaryListener
	 * #onProcessUnstructuredSSResponse(org.mobicents
	 * .protocols.ss7.map.api.service
	 * .supplementary.ProcessUnstructuredSSResponse)
	 */
	public void onProcessUnstructuredSSResponse(ProcessUnstructuredSSResponse procUnstrResInd) {
		if (logger.isDebugEnabled()) {
			try {
				logger.debug(String.format("Rx ProcessUnstructuredSSResponseIndication.  USSD String=%s", procUnstrResInd
                        .getUSSDString().getString(Charset.defaultCharset())));
			} catch (MAPException e) {
				e.printStackTrace();
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.mobicents.protocols.ss7.map.api.service.supplementary.
	 * MAPServiceSupplementaryListener
	 * #onUnstructuredSSNotifyRequest(org.mobicents
	 * .protocols.ss7.map.api.service.supplementary.UnstructuredSSNotifyRequest)
	 */
	public void onUnstructuredSSNotifyRequest(UnstructuredSSNotifyRequest unstrNotifyInd) {
		// This error condition. Client should never receive the
		// UnstructuredSSNotifyRequestIndication
		logger.error(String.format("onUnstructuredSSNotifyRequestIndication for Dialog=%d and invokeId=%d",
				unstrNotifyInd.getMAPDialog().getLocalDialogId(), unstrNotifyInd.getInvokeId()));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.mobicents.protocols.ss7.map.api.service.supplementary.
	 * MAPServiceSupplementaryListener
	 * #onUnstructuredSSNotifyResponse(org.mobicents
	 * .protocols.ss7.map.api.service
	 * .supplementary.UnstructuredSSNotifyResponse)
	 */
	public void onUnstructuredSSNotifyResponse(UnstructuredSSNotifyResponse unstrNotifyInd) {
		// This error condition. Client should never receive the
		// UnstructuredSSNotifyRequestIndication
		logger.error(String.format("onUnstructuredSSNotifyResponseIndication for Dialog=%d and invokeId=%d",
				unstrNotifyInd.getMAPDialog().getLocalDialogId(), unstrNotifyInd.getInvokeId()));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.mobicents.protocols.ss7.map.api.service.supplementary.
	 * MAPServiceSupplementaryListener
	 * #onUnstructuredSSRequest(org.mobicents.protocols
	 * .ss7.map.api.service.supplementary.UnstructuredSSRequest)
	 */
	public void onUnstructuredSSRequest(UnstructuredSSRequest unstrReqInd) {
		if (logger.isDebugEnabled()) {
			try {
				logger.debug(String.format("Rx UnstructuredSSRequestIndication. USSD String=%s ", unstrReqInd
                        .getUSSDString().getString(Charset.defaultCharset())));
			} catch (MAPException e) {
				e.printStackTrace();
			}
		}

		MAPDialogSupplementary mapDialog = unstrReqInd.getMAPDialog();

		try {
			CBSDataCodingScheme ussdDataCodingScheme = new CBSDataCodingSchemeImpl(0x0f);

			USSDString ussdString = this.mapProvider.getMAPParameterFactory().createUSSDString("1");

			AddressString msisdn = this.mapProvider.getMAPParameterFactory().createAddressString(
					AddressNature.international_number, NumberingPlan.ISDN, "31628838002");

			mapDialog.addUnstructuredSSResponse(unstrReqInd.getInvokeId(), ussdDataCodingScheme, ussdString);
			mapDialog.send();

		} catch (MAPException e) {
			logger.error(String.format("Error while sending UnstructuredSSResponse for Dialog=%d",
					mapDialog.getLocalDialogId()));
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.mobicents.protocols.ss7.map.api.service.supplementary.
	 * MAPServiceSupplementaryListener
	 * #onUnstructuredSSResponse(org.mobicents.protocols
	 * .ss7.map.api.service.supplementary.UnstructuredSSResponse)
	 */
	public void onUnstructuredSSResponse(UnstructuredSSResponse unstrResInd) {
		// This error condition. Client should never receive the
		// UnstructuredSSResponseIndication
		logger.error(String.format("onUnstructuredSSResponseIndication for Dialog=%d and invokeId=%d", unstrResInd
				.getMAPDialog().getLocalDialogId(), unstrResInd.getInvokeId()));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.map.api.MAPServiceListener#onErrorComponent
	 * (org.mobicents.protocols.ss7.map.api.MAPDialog, java.lang.Long,
	 * org.mobicents.protocols.ss7.map.api.errors.MAPErrorMessage)
	 */
	public void onErrorComponent(MAPDialog mapDialog, Long invokeId, MAPErrorMessage mapErrorMessage) {
		logger.error(String.format("onErrorComponent for Dialog=%d and invokeId=%d MAPErrorMessage=%s",
				mapDialog.getLocalDialogId(), invokeId, mapErrorMessage));
	}

	public void onRejectComponent(MAPDialog mapDialog, Long aLong, Problem problem, boolean b) {

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.map.api.MAPServiceListener#onInvokeTimeout
	 * (org.mobicents.protocols.ss7.map.api.MAPDialog, java.lang.Long)
	 */
	public void onInvokeTimeout(MAPDialog mapDialog, Long invokeId) {
		logger.error(String.format("onInvokeTimeout for Dialog=%d and invokeId=%d", mapDialog.getLocalDialogId(), invokeId));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.map.api.MAPServiceListener#onMAPMessage(org
	 * .mobicents.protocols.ss7.map.api.MAPMessage)
	 */
	public void onMAPMessage(MAPMessage arg0) {
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.mobicents.protocols.ss7.map.api.MAPServiceListener#
	 * onProviderErrorComponent(org.mobicents.protocols.ss7.map.api.MAPDialog,
	 * java.lang.Long,
	 * org.mobicents.protocols.ss7.map.api.dialog.MAPProviderError)
	 */
	public void onProviderErrorComponent(MAPDialog mapDialog, Long invokeId, MAPProviderAbortReason providerError) {
		logger.error(String.format("onProviderErrorComponent for Dialog=%d and invokeId=%d MAPProviderError=%s",
				mapDialog.getLocalDialogId(), invokeId, providerError));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mobicents.protocols.ss7.map.api.MAPServiceListener#onRejectComponent
	 * (org.mobicents.protocols.ss7.map.api.MAPDialog, java.lang.Long,
	 * org.mobicents.protocols.ss7.tcap.asn.comp.Problem)
	 */
	public void onRejectComponent(MAPDialog mapDialog, Long invokeId, Problem problem) {
		logger.error(String.format("onRejectComponent for Dialog=%d and invokeId=%d Problem=%s",
				mapDialog.getLocalDialogId(), invokeId, problem));
	}

	public static void main(String args[]) {
		System.out.println("*************************************");
		System.out.println("***          SctpClient           ***");
		System.out.println("*************************************");
		IpChannelType ipChannelType = IpChannelType.SCTP;
		if (args.length >= 1 && args[0].toLowerCase().equals("tcp"))
			ipChannelType = IpChannelType.TCP;

		final SctpClient client = new SctpClient();

		try {
			client.initializeStack(ipChannelType);

			// Lets pause for 20 seconds so stacks are initialized properly
			Thread.sleep(20000);

			client.initiateUSSD();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

package uk.gov.dwp.uc.pairtest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TicketServiceImplTest {

    private TicketServiceImpl ticketService;

    private TicketPaymentService ticketPaymentService = mock(TicketPaymentService.class);
    private SeatReservationService seatReservationService = mock(SeatReservationService.class);

    private ArgumentCaptor<Long> accountIdCaptor = ArgumentCaptor.forClass(Long.class);
    private  ArgumentCaptor<Integer> totalAmountToPay = ArgumentCaptor.forClass(Integer.class);
    private  ArgumentCaptor<Integer> totalSeatsToAllocate = ArgumentCaptor.forClass(Integer.class);

    private static final long ACCOUNT_ID = 123L;

    @Before
    public void setUp() {
        ticketService = new TicketServiceImpl(ticketPaymentService, seatReservationService);
    }

    @Test(expected = InvalidPurchaseException.class)
    public void Given_InvalidAccountId_When_PurchasingTickets_Then_ExceptionShouldBeThrown() {
        final TicketTypeRequest adultTickets = new TicketTypeRequest(Type.ADULT, 1);

        ticketService.purchaseTickets(-1L, adultTickets);
    }

    @Test
    public void Given_ValidRequest_When_PurchasingTickets_Then_PaymentServiceAndSeatReservationServiceShouldBeCalledCorrectly() {
        final TicketTypeRequest adultTickets = new TicketTypeRequest(Type.ADULT, 5); // 5 x 20 = 100
        final TicketTypeRequest childTickets = new TicketTypeRequest(Type.CHILD, 4); // 4 x 10 = 40
        final TicketTypeRequest infantTickets = new TicketTypeRequest(Type.INFANT, 3); // 3 x 0 = 0

        ticketService.purchaseTickets(ACCOUNT_ID, adultTickets, childTickets, infantTickets);

        verify(ticketPaymentService, times(1)).makePayment(accountIdCaptor.capture(), totalAmountToPay.capture());
        assertEquals(ACCOUNT_ID, accountIdCaptor.getValue().longValue());
        assertEquals(140, totalAmountToPay.getValue().intValue());

        verify(seatReservationService, times(1)).reserveSeat(accountIdCaptor.capture(), totalSeatsToAllocate.capture());
        assertEquals(ACCOUNT_ID, accountIdCaptor.getValue().longValue());
        assertEquals(9, totalSeatsToAllocate.getValue().intValue());
    }

    @Test
    public void Given_ValidRequestWithZeros_When_PurchasingTickets_Then_PaymentServiceAndSeatReservationServiceShouldBeCalledCorrectly() {
        final TicketTypeRequest adultTickets = new TicketTypeRequest(Type.ADULT, 3); // 3 x 20 = 60
        final TicketTypeRequest childTickets = new TicketTypeRequest(Type.CHILD, 0); // 0 x 10 = 0
        final TicketTypeRequest infantTickets = new TicketTypeRequest(Type.INFANT, 0); // 0 x 0 = 0

        ticketService.purchaseTickets(ACCOUNT_ID, adultTickets, childTickets, infantTickets);

        verify(ticketPaymentService, times(1)).makePayment(accountIdCaptor.capture(), totalAmountToPay.capture());
        assertEquals(ACCOUNT_ID, accountIdCaptor.getValue().longValue());
        assertEquals(60, totalAmountToPay.getValue().intValue());

        verify(seatReservationService, times(1)).reserveSeat(accountIdCaptor.capture(), totalSeatsToAllocate.capture());
        assertEquals(ACCOUNT_ID, accountIdCaptor.getValue().longValue());
        assertEquals(3, totalSeatsToAllocate.getValue().intValue());
    }

    @Test
    public void Given_ValidRequestWithMultiple_When_PurchasingTickets_Then_PaymentServiceAndSeatReservationServiceShouldBeCalledCorrectly(){
        final TicketTypeRequest adultTickets1 = new TicketTypeRequest(Type.ADULT, 3); // 3 x 20 = 60
        final TicketTypeRequest adultTickets2 = new TicketTypeRequest(Type.ADULT, 4); // 4 x 20 = 80
        final TicketTypeRequest childTickets1 = new TicketTypeRequest(Type.CHILD, 3); // 3 x 10 = 30
        final TicketTypeRequest childTickets2 = new TicketTypeRequest(Type.CHILD, 2); // 2 x 10 = 20
        final TicketTypeRequest infantTickets1 = new TicketTypeRequest(Type.INFANT, 3); // 3 x 0 = 0
        final TicketTypeRequest infantTickets2 = new TicketTypeRequest(Type.INFANT, 1); // 1 x 0 = 0

        ticketService.purchaseTickets(ACCOUNT_ID, adultTickets1, childTickets1, infantTickets1, adultTickets2, childTickets2, infantTickets2);

        verify(ticketPaymentService, times(1)).makePayment(accountIdCaptor.capture(), totalAmountToPay.capture());
        assertEquals(ACCOUNT_ID, accountIdCaptor.getValue().longValue());
        assertEquals(190, totalAmountToPay.getValue().intValue());

        verify(seatReservationService, times(1)).reserveSeat(accountIdCaptor.capture(), totalSeatsToAllocate.capture());
        assertEquals(ACCOUNT_ID, accountIdCaptor.getValue().longValue());
        assertEquals(12, totalSeatsToAllocate.getValue().intValue());
    }

    @Test(expected = InvalidPurchaseException.class)
    public void Given_TooManyTickets_When_PurchasingTickets_Then_ExceptionShouldBeThrown(){
        final TicketTypeRequest adultTickets = new TicketTypeRequest(Type.ADULT, 21);

        ticketService.purchaseTickets(ACCOUNT_ID, adultTickets);
    }

    @Test(expected = InvalidPurchaseException.class)
    public void Given_TooManyTicketsOverMultipleRequests_When_PurchasingTickets_Then_ExceptionShouldBeThrown(){
        final TicketTypeRequest adultTickets1 = new TicketTypeRequest(Type.ADULT, 5);
        final TicketTypeRequest adultTickets2 = new TicketTypeRequest(Type.ADULT, 5);
        final TicketTypeRequest adultTickets3 = new TicketTypeRequest(Type.ADULT, 5);
        final TicketTypeRequest adultTickets4 = new TicketTypeRequest(Type.ADULT, 6);

        ticketService.purchaseTickets(ACCOUNT_ID, adultTickets1, adultTickets2, adultTickets3, adultTickets4);
    }

    @Test(expected = InvalidPurchaseException.class)
    public void Given_MoreInfantThanAdults_When_PurchasingTickets_Then_ExceptionShouldBeThrown(){
        final TicketTypeRequest adultTickets = new TicketTypeRequest(Type.ADULT, 4);
        final TicketTypeRequest infantTickets = new TicketTypeRequest(Type.INFANT, 5);

        ticketService.purchaseTickets(ACCOUNT_ID, adultTickets, infantTickets);
    }

    @Test(expected = InvalidPurchaseException.class)
    public void Given_MoreInfantThanAdultsWithMultipleRequests_When_PurchasingTickets_Then_ExceptionShouldBeThrown(){
        final TicketTypeRequest adultTickets1 = new TicketTypeRequest(Type.ADULT, 1);
        final TicketTypeRequest adultTickets2 = new TicketTypeRequest(Type.ADULT, 1);
        final TicketTypeRequest adultTickets3 = new TicketTypeRequest(Type.ADULT, 1);
        final TicketTypeRequest infantTickets1 = new TicketTypeRequest(Type.INFANT, 1);
        final TicketTypeRequest infantTickets2 = new TicketTypeRequest(Type.INFANT, 1);
        final TicketTypeRequest infantTickets3 = new TicketTypeRequest(Type.INFANT, 1);
        final TicketTypeRequest infantTickets4 = new TicketTypeRequest(Type.INFANT, 1);

        ticketService.purchaseTickets(ACCOUNT_ID, adultTickets1, adultTickets2, adultTickets3,
                infantTickets1, infantTickets2, infantTickets3, infantTickets4);
    }

    @Test(expected = InvalidPurchaseException.class)
    public void Given_ChildButNoAdult_When_PurchasingTickets_Then_ExceptionShouldBeThrown(){
        final TicketTypeRequest childTickets = new TicketTypeRequest(Type.CHILD, 4);
        ticketService.purchaseTickets(ACCOUNT_ID, childTickets);
    }

    @Test(expected = InvalidPurchaseException.class)
    public void Given_InfantButNoAdult_When_PurchasingTickets_Then_ExceptionShouldBeThrown(){
        final TicketTypeRequest childTickets = new TicketTypeRequest(Type.INFANT, 4);
        ticketService.purchaseTickets(ACCOUNT_ID, childTickets);
    }

    @Test(expected = InvalidPurchaseException.class)
    public void Given_NegativeTicketNumber_When_PurchasingTickets_Then_ExceptionShouldBeThrown(){
        final TicketTypeRequest adultTickets = new TicketTypeRequest(Type.ADULT, -2);
        final TicketTypeRequest childTickets = new TicketTypeRequest(Type.CHILD, -1);
        final TicketTypeRequest infantTickets = new TicketTypeRequest(Type.INFANT, -1);
        ticketService.purchaseTickets(ACCOUNT_ID, adultTickets, childTickets, infantTickets);
    }

}
package uk.gov.dwp.uc.pairtest;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TicketServiceImpl implements TicketService {

    private static final Map<Type, Integer> PRICE_MAP = Map.of(
                    Type.ADULT, 20,
                    Type.CHILD, 10,
                    Type.INFANT, 0);

    private static final int MAX_TICKET_REQUEST = 20;

    /**
     * Converts a List<TicketTypeRequest> into a Map<Type, Integer>.
     * Multiple requests of the same Type are combined.
     * This Map is used to determine if Business Rules have been obeyed,
     * and calculating seat allocation and total purchase price.
     */
    private static final Function<List<TicketTypeRequest>, Map<Type, Integer>> TICKET_REQUEST_MAPPER =
            ticketTypeRequests -> ticketTypeRequests.stream()
                    .collect(Collectors.groupingBy(TicketTypeRequest::getTicketType,
                            Collectors.summingInt(TicketTypeRequest::getNoOfTickets)));

    /**
     * Counts the required number of seats for a given Map<Type, Integer> of ticket requests.
     * Infants are ignored and assumed to be seated with Adult.
     */
    private static final Function<Map<Type, Integer>, Integer> SEAT_COUNTER =
            seatRequests -> seatRequests.entrySet().stream()
                    .filter(entry -> entry.getKey() != Type.INFANT)
                    .map(Map.Entry::getValue)
                    .mapToInt(Integer::intValue).sum();

    /**
     * Calculates the total cost of ticket requests based in prices in PRICE_MAP.
     */
    private static final Function<Map<Type, Integer>, Integer> PRICE_CALCULATOR =
            ticketMap -> ticketMap.entrySet().stream()
                    .map(entry -> PRICE_MAP.get(entry.getKey()) * entry.getValue())
                    .mapToInt(Integer::intValue).sum();
    /**
     * Rule to determine whether too many tickets have been requested.
     */
    private static final Function<Map<Type, Integer>, Boolean> TOO_MANY_TICKETS_RULE =
            ticketMap -> ticketMap.values().stream().mapToInt(Integer::intValue).sum() > MAX_TICKET_REQUEST;

    /**
     * Rule to determine if too many infant tickets have been requested.
     */
    private static final Function<Map<Type, Integer>, Boolean> MORE_INFANTS_THAN_ADULTS_RULE =
            ticketMap -> ticketMap.getOrDefault(Type.INFANT, 0) > (ticketMap.getOrDefault(Type.ADULT, 0));

    /**
     * Rule to determine if mandatory adult ticket is required.
     */
    private static final Function<Map<Type, Integer>, Boolean> ADULT_MUST_ACCOMPANY_CHILD_INFANT_RULE =
            ticketMap -> ticketMap.getOrDefault(Type.INFANT, 0) + ticketMap.getOrDefault(Type.CHILD, 0) > 0
                    && ticketMap.getOrDefault(Type.ADULT, 0) == 0;

    private static final Function<Map<Type, Integer>, Boolean> NO_NEGATIVE_NUMBER_OF_TICKETS_RULE =
            ticketMap -> ticketMap.values().stream().anyMatch(value -> value < 0);

    /**
     * List of rules to apply to ticket requests.
     * Any additional rule functions can be added here which accept a Map<Type, Integer> and return True/False
     * if rule has been broken.
     */
    private static final List<Function<Map<Type, Integer>, Boolean>> RULES = List.of(
            NO_NEGATIVE_NUMBER_OF_TICKETS_RULE,
            TOO_MANY_TICKETS_RULE,
            MORE_INFANTS_THAN_ADULTS_RULE,
            ADULT_MUST_ACCOMPANY_CHILD_INFANT_RULE);

    private final TicketPaymentService ticketPaymentService;

    private final SeatReservationService seatReservationService;

    public TicketServiceImpl(final TicketPaymentService ticketPaymentService,
                             final SeatReservationService seatReservationService) {
        this.ticketPaymentService = ticketPaymentService;
        this.seatReservationService = seatReservationService;
    }

    /**
     * Should only have private methods other than the one below.
     */
    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests)
            throws InvalidPurchaseException {
        if (accountId <= 0L) {
            throw new InvalidPurchaseException();
        }

        final Map<Type, Integer> ticketRequestMap = TICKET_REQUEST_MAPPER.apply(List.of(ticketTypeRequests));

        validateTicketRequests(ticketRequestMap);
        ticketPaymentService.makePayment(accountId, PRICE_CALCULATOR.apply(ticketRequestMap));
        seatReservationService.reserveSeat(accountId, SEAT_COUNTER.apply(ticketRequestMap));
    }

    /**
     * Apply Business Rules to ticket requests.
     *
     * @param ticketRequests
     * @throws InvalidPurchaseException
     */
    private void validateTicketRequests(final Map<Type, Integer> ticketRequests)
            throws InvalidPurchaseException {
        if (RULES.stream().anyMatch(ruleFunction -> ruleFunction.apply(ticketRequests))) {
            throw new InvalidPurchaseException();
        }
    }

}

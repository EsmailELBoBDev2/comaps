import XCTest
@testable import CoMaps__Debug_

final class CarPlayServiceTests: XCTestCase {

  var carPlayService: CarPlayService!

  override func setUp() {
    super.setUp()
    carPlayService = CarPlayService()
  }

  override func tearDown() {
    carPlayService = nil
    super.tearDown()
  }

  func testCreateEstimates() {
    let routeInfo = RouteInfo(timeToTarget: 100,
                              targetDistance: 25.2,
                              targetUnitsIndex: 1, // km
                              distanceToTurn: 0.5,
                              turnUnitsIndex: 0, // m
                              turnImageName: nil,
                              nextTurnImageName: nil,
                              speedMps: 40.5,
                              speedLimitMps: 60,
                              roundExitNumber: 0,
                              lanes: [],
                              roadName: "Niamiha",
                              roadRef: "",
                              junctionRef: "",
                              destinationRef: "",
                              destination: "",
                              isLink: false,
                              carDirectionIndex: 0,
                              isLeftHandTraffic: false)
    let estimates = carPlayService.createEstimates(routeInfo: routeInfo)

    guard let estimates else {
      XCTFail("Estimates should not be nil.")
      return
    }

    XCTAssertEqual(estimates.distanceRemaining, Measurement<UnitLength>(value: 25.2, unit: .kilometers))
    XCTAssertEqual(estimates.timeRemaining, 100)
  }

  func testLaneWayTurnImageNames() {
    XCTAssertEqual(LaneWay.through.turnImageName, "straight")
    XCTAssertEqual(LaneWay.none.turnImageName, "straight")
    XCTAssertEqual(LaneWay.left.turnImageName, "simple_left")
    XCTAssertEqual(LaneWay.sharpLeft.turnImageName, "sharp_left")
    XCTAssertEqual(LaneWay.slightLeft.turnImageName, "slight_left")
    XCTAssertEqual(LaneWay.mergeToLeft.turnImageName, "slight_left")
    XCTAssertEqual(LaneWay.reverseLeft.turnImageName, "uturn_left")
    XCTAssertEqual(LaneWay.right.turnImageName, "simple_right")
    XCTAssertEqual(LaneWay.sharpRight.turnImageName, "sharp_right")
    XCTAssertEqual(LaneWay.slightRight.turnImageName, "slight_right")
    XCTAssertEqual(LaneWay.mergeToRight.turnImageName, "slight_right")
    XCTAssertEqual(LaneWay.reverseRight.turnImageName, "uturn_right")
  }

  func testInstructionVariants() {
    // Motorway exit: the richest (first) variant joins the exit label, the destination ref and the
    // destinations, and the array is ordered longest-first.
    let exit = NavigationInstructionFormatter.instructionVariants(roadName: "",
                                                                  roadRef: "",
                                                                  junctionRef: "6A",
                                                                  destinationRef: "US 101 South",
                                                                  destination: "San Jose; San Francisco",
                                                                  isLink: true)
    XCTAssertEqual(exit.first, "Exit 6A: US 101 South → San Jose / San Francisco")
    XCTAssertEqual(exit, exit.sorted { $0.count > $1.count })

    // Plain road: ref and name are combined, no brackets.
    let road = NavigationInstructionFormatter.instructionVariants(roadName: "Bayshore Freeway",
                                                                  roadRef: "CA 85",
                                                                  junctionRef: "",
                                                                  destinationRef: "",
                                                                  destination: "",
                                                                  isLink: false)
    XCTAssertEqual(road.first, "CA 85 Bayshore Freeway")

    // No structured data at all yields no variants, so callers keep their fallback.
    let empty = NavigationInstructionFormatter.instructionVariants(roadName: "",
                                                                   roadRef: "",
                                                                   junctionRef: "",
                                                                   destinationRef: "",
                                                                   destination: "",
                                                                   isLink: false)
    XCTAssertTrue(empty.isEmpty)
  }
}

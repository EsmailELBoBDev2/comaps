#pragma once

#include "geometry/point2d.hpp"

#include <cstdint>
#include <limits>

namespace routing
{
enum class SpeedCameraManagerMode;

// CairoDrive: kind of speed camera. The numeric values are written into the mwm
// "speedcams" section (format version >= 1), so DON'T change the existing order.
enum class SpeedCameraType : uint8_t
{
  Unknown = 0,
  Fixed = 1,     // ordinary fixed speed camera
  RedLight = 2,  // red-light / traffic-signal enforcement
  Average = 3,   // average-speed / section control
  Mobile = 4,    // mobile speed trap

  Count
};

struct SpeedCameraOnRoute
{
  SpeedCameraOnRoute() = default;
  SpeedCameraOnRoute(double distFromBegin, uint8_t maxSpeedKmH, m2::PointD const & position,
                     SpeedCameraType type = SpeedCameraType::Unknown)
    : m_distFromBeginMeters(distFromBegin)
    , m_maxSpeedKmH(maxSpeedKmH)
    , m_position(position)
    , m_type(type)
  {}

  static uint8_t constexpr kNoSpeedInfo = std::numeric_limits<uint8_t>::max();

  bool NoSpeed() const;

  bool IsValid() const;
  void Invalidate();

  double m_distFromBeginMeters = 0.0;    // Distance from beginning of route to current camera.
  uint8_t m_maxSpeedKmH = kNoSpeedInfo;  // Maximum speed allowed by the camera.
  m2::PointD m_position = m2::PointD::Max();
  SpeedCameraType m_type = SpeedCameraType::Unknown;  // CairoDrive: camera kind.
};
}  // namespace routing

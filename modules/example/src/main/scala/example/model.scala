// Copyright (c) 2024-2024 by Raphaël Lemaitre and Contributors
// This software is licensed under the Eclipse Public License v2.0 (EPL-2.0).
// For more information see LICENSE or https://opensource.org/license/epl-2-0

package example

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

type UsernameConstraint = DescribedAs[(MinLength[3] & MaxLength[20]), "Must be between 3 and 20 characters"]
type Username           = Username.T
object Username extends RefinedType[String, UsernameConstraint]

type AgeConstraint = DescribedAs[(Positive & Less[150]), "Must be a positive number less than 150"]
type Age           = Age.T
object Age extends RefinedType[Int, AgeConstraint]

type FirstNameConstraint = DescribedAs[Not[Blank], "First name must not be blank"]
type FirstName           = FirstName.T
object FirstName extends RefinedType[String, FirstNameConstraint]

type LastNameConstraint = DescribedAs[Not[Blank], "Last name must not be blank"]
type LastName           = LastName.T
object LastName extends RefinedType[String, LastNameConstraint]

type EmailConstraint = DescribedAs[Match[".*@.*\\..*"], "Must be a valid e-mail"]
type Email           = Email.T
object Email extends RefinedType[String, EmailConstraint]

type CountryNameConstraint = DescribedAs[Not[Blank], "Country name must not be blank"]
type CountryName           = CountryName.T
object CountryName extends RefinedType[String, CountryNameConstraint]

type CountryCodeConstraint = DescribedAs[(FixedLength[2] & LettersUpperCase), "Country name must not be blank"]
type CountryCode           = CountryCode.T
object CountryCode extends RefinedType[String, CountryCodeConstraint]

case class Country(code: CountryCode, name: CountryName, niceName: String)

case class User(
    firstName: Option[FirstName],
    lastName: Option[LastName],
    email: Email,
    age: Option[Age],
    country: Option[CountryCode]
)

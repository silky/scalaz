import java.lang.String
import sbt._

case class TypeClass(name: String, kind: Kind, pack: Seq[String] = Seq("scalaz"), extendsList: Seq[TypeClass] = Seq()) {
  require(pack.head == "scalaz")
  def syntaxPack = {
    Seq("scalaz", "syntax") ++ pack.drop(1)
  }

  def packageString0 = pack.map("package " + _).mkString("\n")
  def packageString = pack.mkString(".")
}

object TypeClass {
  import Kind._

  lazy val semigroup = TypeClass("Semigroup", *)
  lazy val monoid = TypeClass("Monoid", *, extendsList = Seq(semigroup))
  lazy val equal = TypeClass("Equal", *)
  lazy val show = TypeClass("Show", *)
  lazy val order = TypeClass("Order", *, extendsList = Seq(equal))
  lazy val metricSpace = TypeClass("MetricSpace", *)

  lazy val length = TypeClass("Length", *->*)
  lazy val each = TypeClass("Each", *->*)
  lazy val index = TypeClass("Index", *->*)
  lazy val empty = TypeClass("Empty", *->*)
  lazy val functor = TypeClass("Functor", *->*)
  lazy val pointed = TypeClass("Pointed", *->*, extendsList = Seq(functor))
  lazy val apply: TypeClass = TypeClass("Apply", *->*, extendsList = Seq(functor))
  lazy val applicative = TypeClass("Applicative", *->*, extendsList = Seq(apply, pointed))
  lazy val bind = TypeClass("Bind", *->*, extendsList = Seq(apply))
  lazy val monad = TypeClass("Monad", *->*, extendsList = Seq(applicative, bind))
  lazy val traverse = TypeClass("Traverse", *->*, extendsList = Seq(functor))

  lazy val contravariant = TypeClass("Contravariant", *->*)
  lazy val copointed = TypeClass("Copointed", *->*)
  lazy val cojoin = TypeClass("Cojoin", *->*)
  lazy val comonad = TypeClass("Comonad", *->*, extendsList = Seq(copointed, cojoin))

  lazy val plus = TypeClass("Plus", *->*, extendsList = Seq(functor, empty))
  lazy val applicativePlus = TypeClass("ApplicativePlus", *->*, extendsList = Seq(applicative, plus))
  lazy val monadPlus = TypeClass("MonadPlus", *->*, extendsList = Seq(monad, applicativePlus))

  lazy val biFunctor = TypeClass("BiFunctor", *^*->*)
  lazy val biTraverse = TypeClass("BiTraverse", *^*->*, extendsList = Seq(biFunctor))
  lazy val arr = TypeClass("Arr", *^*->*)
  lazy val arrId = TypeClass("ArrId", *^*->*)
  lazy val compose = TypeClass("Compose", *^*->*)
  lazy val category = TypeClass("Category", *^*->*, extendsList = Seq(arrId, compose))
  lazy val first = TypeClass("First", *^*->*)
  lazy val arrow = TypeClass("Arrow", *^*->*, extendsList = Seq(category, arr, first))

  lazy val run = TypeClass("Run", *, pack = Seq("scalaz", "concurrent"))


  //   Not automatically generated.
  //  lazy val monadState = TypeClass("MonadState", *^*->*, monad)

  def all: List[TypeClass] = List(semigroup,
    monoid,
    equal,
    length,
    show,
    order,
    metricSpace,
    empty,
    each,
    index,
    functor,
    pointed,
    contravariant,
    copointed,
    apply,
    applicative,
    bind,
    monad,
    cojoin,
    comonad,
    plus,
    applicativePlus,
    monadPlus,
    traverse,
    biFunctor,
    biTraverse,
    arrId,
    arr,
    compose,
    category,
    first,
    arrow,

    run
  )
}

sealed abstract class Kind

object Kind {

  case object * extends Kind

  case object *->* extends Kind
  
  case object *^*->* extends Kind
}

object GenTypeClass {

  case class SourceFile(packages: Seq[String], fileName: String, source: String) {
    def file(scalaSource: File): File = packages.foldLeft(scalaSource)((file, p) => file / p) / fileName

    def createOrUpdate(scalaSource: File, log: Logger): sbt.File = {
      val f = file(scalaSource)
      val updatedSource = if (f.exists()) {
        val old = IO.read(f)
        log.info("Updating %s".format(f))
        updateSource(old)
      } else {
        log.info("Creating %s".format(f))
        source
      }
      log.debug("Contents: %s".format(updatedSource))
      IO.write(f, updatedSource)
      f
    }

    def updateSource(oldSource: String): String = {
      val delimiter = "////"
      def parse(text: String): Seq[String] = {
        text.split(delimiter)
      }
      val oldChunks: Seq[String] = parse(oldSource)
      val newChunks: Seq[String] = parse(source)
      if (oldChunks.length != newChunks.length) error("different number of chunks in old and new source: " + fileName)

      val updatedChunks = for {
        ((o, n), i) <- oldChunks.zip(newChunks).zipWithIndex
      } yield {
        val useOld = i % 2 == 1
        if (useOld) o else n
      }
      updatedChunks.mkString(delimiter)
    }
  }

  case class TypeClassSource(mainFile: SourceFile, syntaxFile: SourceFile) {
    def sources = List(mainFile, syntaxFile)
  }

  def typeclassSource(tc: TypeClass): TypeClassSource = {
    val typeClassName = tc.name
    val kind = tc.kind
    val extendsList = tc.extendsList.toList.map(_.name)

    import TypeClass._
    val classifiedTypeIdent = if (Set(arr, arrId, category, compose)(tc)) "=>:"
    else "F"

    val typeShape: String = kind match {
      case Kind.* => ""
      case Kind.*->* => "[_]"
      case Kind.*^*->* => "[_, _]"
    }
    val classifiedType = classifiedTypeIdent +  typeShape

    val classifiedTypeF = "F" +  typeShape

    def initLower(s: String) = {
      val (init, rest) = s.splitAt(1)
      init.toLowerCase + rest
    }
    def extendsListText(suffix: String, parents: Seq[String] = extendsList, cti: String = classifiedTypeIdent) = parents match {
      case Seq() => ""
      case es => es.map(n => n + suffix + "[" + cti + "]").mkString("extends ", " with ", "")
    }
    def extendsToSyntaxListText = extendsList match {
      case Seq() => ""
      case es => es.map(n => "To" + n + "Syntax").mkString("extends ", " with ", "")
    }
    val extendsLikeList = extendsListText("")

    val syntaxPackString = tc.syntaxPack.map("package " + _).mkString("\n")
    val syntaxPackString1 = tc.syntaxPack.mkString(".")
    val syntaxMember = "val %sSyntax = new %s.%sSyntax[%s] {}".format(initLower(typeClassName), syntaxPackString1, typeClassName, classifiedTypeIdent)

    val mainSource = """%s

trait %s[%s] %s { self =>
  ////

  // derived functions

  ////
  %s
}

object %s {
  def apply[%s](implicit F: %s[F]): %s[F] = F

  ////

  ////
}

""".format(tc.packageString0, typeClassName, classifiedType, extendsLikeList, syntaxMember,
      typeClassName,
      classifiedTypeF, typeClassName, typeClassName, typeClassName, classifiedTypeIdent, classifiedTypeIdent
      )
    val mainSourceFile = SourceFile(tc.pack, typeClassName + ".scala", mainSource)

    val syntaxSource = kind match {
      case Kind.* =>
        """%s

/** Wraps a value `self` and provides methods related to `%s` */
trait %sV[F] extends SyntaxV[F] {
  ////

  ////
}

trait To%sSyntax %s {
  implicit def To%sV[F](v: F) =
    new %sV[F] { def self = v }

  ////

  ////
}

trait %sSyntax[F] %s {
  implicit def To%sV(v: F): %sV[F] = new %sV[F] { def self = v }

  ////

  ////
}
""".format(syntaxPackString, typeClassName, typeClassName, typeClassName, extendsToSyntaxListText,

      // implicits in ToXxxSyntax
      typeClassName, typeClassName,

      typeClassName, extendsListText("Syntax", cti = "F"),
      typeClassName, typeClassName, typeClassName
    )
    case Kind.*->* =>
    """%s

/** Wraps a value `self` and provides methods related to `%s` */
trait %sV[F[_],A] extends SyntaxV[F[A]] {
  ////

  ////
}

trait To%sSyntax %s {
  implicit def To%sV[F[_],A](v: F[A]) =
    new %sV[F,A] { def self = v }
  implicit def To%sVFromBin[F[_, _], X, A](v: F[X, A]) =
    new %sV[({type f[a] = F[X, a]})#f,A] { def self = v }
  implicit def To%sVFromBinT[F[_, _[_], _], G[_], X, A](v: F[X, G, A]) =
    new %sV[({type f[a] = F[X, G, a]})#f,A] { def self = v }
  implicit def To%sVFromBinTId[F[_, _[_], _], X, A](v: F[X, Id, A]) =
    new %sV[({type f[a] = F[X, Id, a]})#f,A] { def self = v }

  ////

  ////
}

trait %sSyntax[F[_]] %s {
  implicit def To%sV[A](v: F[A]): %sV[F, A] = new %sV[F,A] { def self = v }

  ////

  ////
}
""".format(syntaxPackString, typeClassName, typeClassName, typeClassName, extendsToSyntaxListText,

          // implicits in ToXxxSyntax
          typeClassName, typeClassName,
          typeClassName, typeClassName,
          typeClassName, typeClassName,
          typeClassName, typeClassName,

          typeClassName, extendsListText("Syntax", cti = "F"),
          typeClassName, typeClassName, typeClassName
        )
      case Kind.*^*->* =>
    """%s

/** Wraps a value `self` and provides methods related to `%s` */
trait %sV[F[_, _],A, B] extends SyntaxV[F[A, B]] {
  ////

  ////
}

trait To%sSyntax %s {
  implicit def To%sV[F[_, _],A, B](v: F[A, B]) =
    new %sV[F,A, B] { def self = v }

  ////

  ////
}

trait %sSyntax[F[_, _]] %s {
  implicit def To%sV[A, B](v: F[A, B]): %sV[F, A, B] = new %sV[F, A, B] { def self = v }

  ////

  ////
}
""".format(syntaxPackString, typeClassName, typeClassName, typeClassName, extendsToSyntaxListText,

          // implicits in ToXxxSyntax
          typeClassName, typeClassName,

          typeClassName, extendsListText("Syntax", cti = "F"),
          typeClassName, typeClassName, typeClassName
        )
    }
    val syntaxSourceFile = SourceFile(tc.syntaxPack, typeClassName + "Syntax.scala", syntaxSource)
    TypeClassSource(mainSourceFile, syntaxSourceFile)
  }
}